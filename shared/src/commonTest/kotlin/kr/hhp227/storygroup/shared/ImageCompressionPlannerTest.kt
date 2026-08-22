package kr.hhp227.storygroup.shared

import kr.hhp227.storygroup.shared.domain.media.ImageCompressionPlanner
import kr.hhp227.storygroup.shared.domain.media.ImageFormat
import kr.hhp227.storygroup.shared.domain.media.ImagePlan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ImageCompressionPlannerTest {
    @Test
    fun GIF는_애니메이션_보존을_위해_무조건_생략() {
        assertIs<ImagePlan.Skip>(ImageCompressionPlanner.plan("image/gif", 9_000_000, 4000, 3000))
    }

    @Test
    fun 작고_치수도_작으면_생략() {
        assertIs<ImagePlan.Skip>(ImageCompressionPlanner.plan("image/jpeg", 1024 * 1024, 1920, 1080))
    }

    @Test
    fun 크기_1MB_초과면_치수가_작아도_재인코딩() {
        val r = assertIs<ImagePlan.Recompress>(ImageCompressionPlanner.plan("image/jpeg", 3_000_000, 1600, 900))
        assertEquals(1600, r.targetWidth) // 치수는 유지, 재인코딩만
        assertEquals(900, r.targetHeight)
    }

    @Test
    fun 긴_변_1920_초과면_비율_유지_축소() {
        val r = assertIs<ImagePlan.Recompress>(ImageCompressionPlanner.plan("image/jpeg", 500_000, 4000, 3000))
        assertEquals(1920, r.targetWidth)
        assertEquals(1440, r.targetHeight)
    }

    @Test
    fun PNG는_투명도_보존을_위해_PNG로_남는다() {
        val r = assertIs<ImagePlan.Recompress>(ImageCompressionPlanner.plan("image/png", 5_000_000, 2400, 2400))
        assertEquals(ImageFormat.PNG, r.format)
    }

    @Test
    fun HEIC_등_그_외는_JPEG로() {
        val r = assertIs<ImagePlan.Recompress>(ImageCompressionPlanner.plan("image/heic", 5_000_000, 4032, 3024))
        assertEquals(ImageFormat.JPEG, r.format)
    }
}
