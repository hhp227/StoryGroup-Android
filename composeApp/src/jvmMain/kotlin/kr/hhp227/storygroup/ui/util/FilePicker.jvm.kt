package kr.hhp227.storygroup.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import storygroup.composeapp.generated.resources.Res
import storygroup.composeapp.generated.resources.picker_file
import ws.schild.jave.MultimediaObject
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.nio.file.Files

/** OS 네이티브 파일 다이얼로그(모달) — 확장자 제한 없음, 신규 의존성 없이 java.awt만 사용 */
@Composable
actual fun rememberFilePickerLauncher(onPicked: (PickedFile) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val title = stringResource(Res.string.picker_file)

    return {
        // FileDialog.isVisible=true는 모달 블로킹 호출 — Dispatchers.IO에서 열어 Swing EDT를 막지 않는다
        scope.launch(Dispatchers.IO) {
            val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
            dialog.isVisible = true

            val directory = dialog.directory
            val fileName = dialog.file
            if (directory != null && fileName != null) {
                val file = File(directory, fileName)
                val contentType = runCatching { Files.probeContentType(file.toPath()) }.getOrNull()
                    ?: "application/octet-stream"
                val picked = if (contentType.startsWith("video/")) {
                    // 동영상은 압축 대상(§4-b) — 경로+메타로 전달(ImagePicker.jvm 동영상 분기 미러)
                    val info = runCatching { MultimediaObject(file).info }.getOrNull()
                    val videoSize = info?.video?.size
                    PickedFile(
                        bytes = ByteArray(0), fileName = fileName, contentType = contentType,
                        filePath = file.absolutePath, durationMs = info?.duration ?: 0L,
                        width = videoSize?.width ?: 0, height = videoSize?.height ?: 0, sizeBytes = file.length()
                    )
                } else {
                    PickedFile(file.readBytes(), fileName, contentType)
                }

                withContext(Dispatchers.Main) {
                    onPicked(picked)
                }
            }
        }
    }
}
