package kr.hhp227.storygroup.shared.domain.media

/**
 * 동영상 압축 판정 — 4개 플랫폼(웹 TS 미러 포함)이 같은 수식을 쓴다.
 * 웹 미러: storygroup-web/src/lib/video-compression-plan.ts — 수정 시 양쪽 함께.
 * 판정 순서: 500MB 초과 거부 → 5MB 이하 생략(길이 무관) → 3분 초과 거부 → 플랜 계산.
 * 실행기는 이 결과대로만 인코딩한다(재시도는 호출부가 RETRY_MARGIN으로 재플랜).
 */
sealed interface VideoPlan {
    data object RejectTooLarge : VideoPlan
    data object RejectTooLong : VideoPlan
    data object SkipAlreadySmall : VideoPlan
    data class Compress(
        val videoBitrate: Int,
        val audioBitrate: Int,
        val targetWidth: Int,
        val targetHeight: Int,
        val fps: Int
    ) : VideoPlan
}

object VideoCompressionPlanner {
    const val TARGET_BYTES = 5L * 1024 * 1024
    const val MAX_SOURCE_BYTES = 500L * 1024 * 1024
    const val MAX_DURATION_MS = 180_000L
    const val AUDIO_BITRATE = 64_000
    const val FIRST_MARGIN = 0.93
    const val RETRY_MARGIN = 0.85
    const val MIN_VIDEO_BITRATE = 100_000
    const val FPS = 30

    fun plan(durationMs: Long, sizeBytes: Long, width: Int, height: Int, margin: Double = FIRST_MARGIN): VideoPlan {
        if (sizeBytes > MAX_SOURCE_BYTES) return VideoPlan.RejectTooLarge
        if (sizeBytes <= TARGET_BYTES) return VideoPlan.SkipAlreadySmall
        if (durationMs > MAX_DURATION_MS) return VideoPlan.RejectTooLong

        val durationSec = durationMs / 1000.0
        val totalBitrate = (TARGET_BYTES * 8 * margin / durationSec).toInt()
        val videoBitrate = (totalBitrate - AUDIO_BITRATE).coerceAtLeast(MIN_VIDEO_BITRATE)
        val targetShort = when {
            videoBitrate >= 1_500_000 -> 720
            videoBitrate >= 800_000 -> 540
            videoBitrate >= 400_000 -> 480
            else -> 360
        }
        val (w, h) = scaleToShortSide(width, height, targetShort)
        return VideoPlan.Compress(videoBitrate, AUDIO_BITRATE, w, h, FPS)
    }

    /** 짧은 변을 목표로 비율 유지 축소 — 업스케일 없음, H.264 제약으로 짝수 내림 */
    private fun scaleToShortSide(width: Int, height: Int, targetShort: Int): Pair<Int, Int> {
        val shortSide = minOf(width, height)
        if (shortSide <= targetShort || shortSide <= 0) return even(width) to even(height)
        val scale = targetShort.toDouble() / shortSide
        return even((width * scale).toInt()) to even((height * scale).toInt())
    }

    private fun even(v: Int) = (v - v % 2).coerceAtLeast(2)
}
