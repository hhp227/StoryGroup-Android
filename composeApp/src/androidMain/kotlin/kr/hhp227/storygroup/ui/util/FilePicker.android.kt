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
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }

                bytes?.let { PickedFile(it, fileName, contentType) }
            }
            picked?.let(onPicked)
        }
    }
    return { launcher.launch(arrayOf("*/*")) }
}
