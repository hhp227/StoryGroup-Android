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
actual fun rememberImagePickerLauncher(onPicked: (PickedImage) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()

    return {
        // FileDialog.isVisible=true는 모달 블로킹 호출 — Dispatchers.IO에서 열어 Swing EDT를 막지 않는다
        scope.launch(Dispatchers.IO) {
            val dialog = FileDialog(null as Frame?, "이미지 선택", FileDialog.LOAD)
            dialog.file = "*.jpg;*.jpeg;*.png;*.webp;*.gif"
            dialog.isVisible = true

            val directory = dialog.directory
            val fileName = dialog.file
            if (directory != null && fileName != null) {
                val bytes = File(directory, fileName).readBytes()
                withContext(Dispatchers.Main) {
                    onPicked(PickedImage(bytes, fileName, contentTypeFor(fileName)))
                }
            }
        }
    }
}

private fun contentTypeFor(fileName: String): String =
    when (fileName.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        else -> "image/jpeg"
    }
