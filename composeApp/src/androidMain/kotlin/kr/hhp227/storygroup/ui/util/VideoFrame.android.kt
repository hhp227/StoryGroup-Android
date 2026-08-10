package kr.hhp227.storygroup.ui.util

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 프레임 한 변의 상한 — 가장 큰 쓰임이 화면 폭 16:9라 이 정도면 충분하고, 원본 1080p를 그대로 들면 8MB짜리 비트맵이 된다 */
private const val MAX_FRAME_PX = 640

/** 피드를 오르내려도 다시 뽑지 않을 만큼만 들고 있는다 — 디스크 캐시는 없어 앱을 다시 켜면 한 번 더 받는다 */
private const val MAX_CACHED_FRAMES = 24

// accessOrder=true라 최근에 쓴 것이 뒤로 간다(LRU). 컴포지션(메인 스레드)에서만 건드린다.
private val frameCache = object : LinkedHashMap<String, ImageBitmap>(0, 0.75f, true) {
    override fun removeEldestEntry(eldest: Map.Entry<String, ImageBitmap>): Boolean = size > MAX_CACHED_FRAMES
}

@Composable
actual fun rememberVideoFrame(url: String): ImageBitmap? =
    produceState(initialValue = frameCache[url], url) {
        if (value != null) return@produceState

        val frame = withContext(Dispatchers.IO) { extractFirstFrame(url) }
        if (frame != null) {
            frameCache[url] = frame
            value = frame
        }
    }.value

/**
 * 첫 프레임 한 장. 실패는 조용히 null로 흘린다 — 코덱 미지원·네트워크 실패 모두
 * "포스터가 없다"로 같게 취급하고 호출부가 검은 자리로 폴백한다.
 */
private fun extractFirstFrame(url: String): ImageBitmap? = runCatching {
    val retriever = MediaMetadataRetriever()

    try {
        // 헤더는 서버가 붙인 공개 URL이라 필요 없다(인증 없이 열린다)
        retriever.setDataSource(url, emptyMap())
        // OPTION_CLOSEST_SYNC는 0 근처의 키프레임 — 정확히 0을 요구하면 디코딩이 더 비싸다
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, MAX_FRAME_PX, MAX_FRAME_PX)
        } else {
            // getScaledFrameAtTime은 API 27+ — 그 아래선 원본을 받아 직접 줄인다
            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let(::downscale)
        }

        bitmap?.asImageBitmap()
    } finally {
        // AutoCloseable은 API 29+라 손으로 닫는다
        retriever.release()
    }
}.getOrNull()

private fun downscale(bitmap: Bitmap): Bitmap {
    val longestSide = maxOf(bitmap.width, bitmap.height)
    if (longestSide <= MAX_FRAME_PX) return bitmap

    val scale = MAX_FRAME_PX.toFloat() / longestSide
    val scaled = Bitmap.createScaledBitmap(
        bitmap,
        (bitmap.width * scale).toInt().coerceAtLeast(1),
        (bitmap.height * scale).toInt().coerceAtLeast(1),
        true
    )
    if (scaled !== bitmap) bitmap.recycle()

    return scaled
}
