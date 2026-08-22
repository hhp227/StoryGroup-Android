package kr.hhp227.storygroup.ui.util

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** SAF 문서 피커(OpenDocument) — 저장소 권한 없이 모든 MIME을 고를 수 있다 */
@Composable
actual fun rememberFilePickerLauncher(onPicked: (PickedFile) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult

        scope.launch {
            val picked = withContext(Dispatchers.IO) {
                val contentType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
                } ?: "attachment"
                if (contentType.startsWith("video/")) {
                    // 동영상은 압축 대상(§4-b) — 경로+메타로 전달, 원본을 메모리에 올리지 않는다
                    val extension = contentType.substringAfter('/', "mp4")
                    cacheVideoFromUri(context, uri, extension)?.let { cached ->
                        PickedFile(
                            bytes = ByteArray(0), fileName = fileName, contentType = contentType,
                            filePath = cached.path, durationMs = cached.durationMs,
                            width = cached.width, height = cached.height, sizeBytes = cached.sizeBytes
                        )
                    }
                } else {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }

                    bytes?.let { PickedFile(it, fileName, contentType) }
                }
            }
            picked?.let(onPicked)
        }
    }
    return { launcher.launch(arrayOf("*/*")) }
}
