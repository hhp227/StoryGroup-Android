package kr.hhp227.storygroup.shared

import kr.hhp227.storygroup.shared.domain.media.VideoCompressionPlanner
import kr.hhp227.storygroup.shared.domain.media.VideoPlan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class VideoCompressionPlannerTest {
    private fun plan(
        durationMs: Long = 30_000,
        sizeBytes: Long = 50L * 1024 * 1024,
        width: Int = 1920,
        height: Int = 1080,
        margin: Double = VideoCompressionPlanner.FIRST_MARGIN
    ) = VideoCompressionPlanner.plan(durationMs, sizeBytes, width, height, margin)

    @Test
    fun 원본_500MB_초과는_거부() {
        assertIs<VideoPlan.RejectTooLarge>(plan(sizeBytes = 500L * 1024 * 1024 + 1))
    }

    @Test
    fun 원본_5MB_이하는_길이와_무관하게_생략() {
        // 10분짜리여도 원본이 이미 작으면 재인코딩하지 않는다(§2-3)
        assertIs<VideoPlan.SkipAlreadySmall>(plan(durationMs = 600_000, sizeBytes = 5L * 1024 * 1024))
    }

    @Test
    fun 길이_3분_초과는_거부() {
        assertIs<VideoPlan.RejectTooLong>(plan(durationMs = 180_001))
    }

    @Test
    fun 비트레이트는_목표용량과_마진에서_계산된다() {
        // 30초: 5MB×8×0.93/30 − 64k = 1,300,234 − 64,000 = 1,236,234bps
        val c = assertIs<VideoPlan.Compress>(plan(durationMs = 30_000))
        assertEquals(1_236_234, c.videoBitrate)
        assertEquals(64_000, c.audioBitrate)
    }

    @Test
    fun 해상도_사다리_경계() {
        // 30초 → ~1.24Mbps → 1.5M 미만이므로 540
        assertEquals(540, minSide(plan(durationMs = 30_000)))
        // 20초 → 5MB×8×0.93/20−64k ≈ 1.89Mbps → 720
        assertEquals(720, minSide(plan(durationMs = 20_000)))
        // 90초 → ≈369kbps → 360
        assertEquals(360, minSide(plan(durationMs = 90_000)))
        // 60초 → ≈586kbps → 480
        assertEquals(480, minSide(plan(durationMs = 60_000)))
    }

    @Test
    fun 세로_영상은_가로가_짧은_변이다() {
        val c = assertIs<VideoPlan.Compress>(plan(durationMs = 90_000, width = 1080, height = 1920))
        assertEquals(360, c.targetWidth)
        assertEquals(640, c.targetHeight)
    }

    @Test
    fun 원본보다_키우지_않고_치수는_항상_짝수() {
        val c = assertIs<VideoPlan.Compress>(plan(durationMs = 20_000, width = 640, height = 361))
        assertEquals(640, c.targetWidth)   // 짧은 변 361 ≤ 720 → 업스케일 없음
        assertEquals(360, c.targetHeight)  // 홀수는 내림 짝수화(H.264 제약)
    }

    @Test
    fun 최장_3분은_최저_클램프_위의_저비트레이트() {
        // 180초: 216,705 − 64,000 = 152,705bps (클램프 100k 위)
        val c = assertIs<VideoPlan.Compress>(plan(durationMs = 180_000))
        assertEquals(152_705, c.videoBitrate)
        assertTrue(c.videoBitrate >= 100_000)
    }

    @Test
    fun 재시도_마진은_더_보수적이다() {
        val first = assertIs<VideoPlan.Compress>(plan(durationMs = 30_000))
        val retry = assertIs<VideoPlan.Compress>(plan(durationMs = 30_000, margin = VideoCompressionPlanner.RETRY_MARGIN))
        assertTrue(retry.videoBitrate < first.videoBitrate)
    }

    private fun minSide(p: VideoPlan) = (p as VideoPlan.Compress).let { minOf(it.targetWidth, it.targetHeight) }
}
