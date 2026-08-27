package kr.hhp227.storygroup.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import storygroup.composeapp.generated.resources.Res
import storygroup.composeapp.generated.resources.picker_image
import storygroup.composeapp.generated.resources.picker_video
import ws.schild.jave.MultimediaObject
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** OS 네이티브 파일 다이얼로그(모달) — 신규 의존성 없이 java.awt만 사용 */
@Composable
actual fun rememberImagePickerLauncher(mode: PickerMode, onPicked: (PickedImage) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val title = stringResource(if (mode == PickerMode.Video) Res.string.picker_video else Res.string.picker_image)

    return {
        // FileDialog.isVisible=true는 모달 블로킹 호출 — Dispatchers.IO에서 열어 Swing EDT를 막지 않는다
        scope.launch(Dispatchers.IO) {
            val isVideo = mode == PickerMode.Video
            val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
            // FileDialog.file 필터는 OS에 따라 무시되기도 한다 — 걸러지지 않은 파일은
            // contentTypeFor가 video/mp4·image/jpeg로 떨어뜨리고 서버가 최종 판정한다
            dialog.file = if (isVideo) "*.mp4;*.mov;*.webm;*.mkv" else "*.jpg;*.jpeg;*.png;*.webp;*.gif"
            dialog.isVisible = true

            val directory = dialog.directory
            val fileName = dialog.file
            if (directory != null && fileName != null) {
                val file = File(directory, fileName)
                val picked = if (isVideo) {
                    // 압축기(jave2)가 경로로 받는다 — 원본을 메모리에 올리지 않는다.
                    // 메타 판독 실패면 durationMs=0으로 두고 인코더가 실패를 보고하게 한다(여기서 막지 않음)
                    val info = runCatching { MultimediaObject(file).info }.getOrNull()
                    val videoSize = info?.video?.size
                    PickedImage(
                        bytes = ByteArray(0), fileName = fileName, contentType = contentTypeFor(fileName, true),
                        filePath = file.absolutePath, durationMs = info?.duration ?: 0L,
                        width = videoSize?.width ?: 0, height = videoSize?.height ?: 0, sizeBytes = file.length()
                    )
                } else {
                    PickedImage(file.readBytes(), fileName, contentTypeFor(fileName, false))
                }
                withContext(Dispatchers.Main) {
                    onPicked(picked)
                }
            }
        }
    }
}

private fun contentTypeFor(fileName: String, isVideo: Boolean): String {
    val extension = fileName.substringAfterLast('.', "").lowercase()

    return if (isVideo) {
        when (extension) {
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            else -> "video/mp4"
        }
    } else {
        when (extension) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> "image/jpeg"
        }
    }
}
