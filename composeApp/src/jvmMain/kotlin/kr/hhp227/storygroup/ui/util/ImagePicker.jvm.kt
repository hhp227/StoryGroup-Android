package kr.hhp227.storygroup.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** OS 네이티브 파일 다이얼로그(모달) — 신규 의존성 없이 java.awt만 사용 */
@Composable
actual fun rememberImagePickerLauncher(mode: PickerMode, onPicked: (PickedImage) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()

    return {
        // FileDialog.isVisible=true는 모달 블로킹 호출 — Dispatchers.IO에서 열어 Swing EDT를 막지 않는다
        scope.launch(Dispatchers.IO) {
            val isVideo = mode == PickerMode.Video
            val dialog = FileDialog(null as Frame?, if (isVideo) "동영상 선택" else "이미지 선택", FileDialog.LOAD)
            // FileDialog.file 필터는 OS에 따라 무시되기도 한다 — 걸러지지 않은 파일은
            // contentTypeFor가 video/mp4·image/jpeg로 떨어뜨리고 서버가 최종 판정한다
            dialog.file = if (isVideo) "*.mp4;*.mov;*.webm;*.mkv" else "*.jpg;*.jpeg;*.png;*.webp;*.gif"
            dialog.isVisible = true

            val directory = dialog.directory
            val fileName = dialog.file
            if (directory != null && fileName != null) {
                val bytes = File(directory, fileName).readBytes()
                withContext(Dispatchers.Main) {
                    onPicked(PickedImage(bytes, fileName, contentTypeFor(fileName, isVideo)))
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
