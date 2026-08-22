package kr.hhp227.storygroup.ui.util

import android.media.MediaMetadataRetriever
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
import java.io.File
import java.util.UUID

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
                // 확장자는 컨텐츠타입에서 되뽑는다 — 서버가 저장 이름을 UUID+확장자로 만들기 때문
                val extension = contentType.substringAfter('/', if (mode == PickerMode.Video) "mp4" else "jpg")
                if (mode == PickerMode.Video) {
                    cacheVideoFromUri(context, uri, extension)?.let { cached ->
                        PickedImage(
                            bytes = ByteArray(0), fileName = "upload.$extension", contentType = contentType,
                            filePath = cached.path, durationMs = cached.durationMs,
                            width = cached.width, height = cached.height, sizeBytes = cached.sizeBytes
                        )
                    }
                } else {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    bytes?.let { PickedImage(it, fileName = "upload.$extension", contentType) }
                }
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

/** 캐시된 동영상 한 건 — 압축 판정(Planner) 입력 메타를 함께 나른다 */
internal class CachedVideo(
    val path: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Long
)

/**
 * 동영상 URI → cacheDir 스트림 복사+메타데이터 추출 — ImagePicker(사진첩)와 FilePicker(문서 피커)가
 * 공유한다. 압축 워커가 경로로 받으므로 원본을 메모리에 올리지 않는다.
 */
internal fun cacheVideoFromUri(context: android.content.Context, uri: Uri, extension: String): CachedVideo? {
    val cached = File(context.cacheDir, "picked-${UUID.randomUUID()}.$extension")
    context.contentResolver.openInputStream(uri)?.use { input ->
        cached.outputStream().use { input.copyTo(it) }
    } ?: return null
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(cached.absolutePath)
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        // 회전 메타가 90/270이면 표시 방향 기준으로 스왑 — Planner는 표시 치수를 받는다
        val (width, height) = if (rotation == 90 || rotation == 270) rawHeight to rawWidth else rawWidth to rawHeight
        CachedVideo(cached.absolutePath, durationMs, width, height, cached.length())
    } finally {
        retriever.release()
    }
}
