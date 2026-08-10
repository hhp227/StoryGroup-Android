package kr.hhp227.storygroup.ui.util

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 시스템 포토 피커(Android 13+ 네이티브, 이하는 Play services 백포트) — 갤러리 접근 권한이 필요 없다 */
@Composable
actual fun rememberImagePickerLauncher(mode: PickerMode, onPicked: (PickedImage) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fallbackContentType = if (mode == PickerMode.Video) "video/mp4" else "image/jpeg"
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult

        scope.launch {
            val picked = withContext(Dispatchers.IO) {
                val contentType = context.contentResolver.getType(uri) ?: fallbackContentType
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                // 확장자는 컨텐츠타입에서 되뽑는다 — 서버가 저장 이름을 UUID+확장자로 만들기 때문
                val extension = contentType.substringAfter('/', if (mode == PickerMode.Video) "mp4" else "jpg")
                bytes?.let { PickedImage(it, fileName = "upload.$extension", contentType) }
            }
            picked?.let(onPicked)
        }
    }
    val request = PickVisualMediaRequest(
        when (mode) {
            PickerMode.Image -> ActivityResultContracts.PickVisualMedia.ImageOnly
            PickerMode.Video -> ActivityResultContracts.PickVisualMedia.VideoOnly
        }
    )

    return { launcher.launch(request) }
}
