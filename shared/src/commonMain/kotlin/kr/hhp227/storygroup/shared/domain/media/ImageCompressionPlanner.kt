package kr.hhp227.storygroup.shared.domain.media

/**
 * 이미지 압축 판정(§3) — GIF는 재인코딩하면 애니메이션이 깨져 무조건 생략(앨범 GIF 뱃지 유지).
 * PNG는 투명도 보존을 위해 PNG 출력, 그 외(JPEG/WebP/HEIC)는 JPEG q0.85.
 * 웹 미러: storygroup-web/src/lib/image-compression-plan.ts — 수정 시 양쪽 함께.
 * 실행·디코딩 실패 시 원본 폴백은 실행기(ImageCompressor 구현) 책임.
 */
sealed interface ImagePlan {
    data object Skip : ImagePlan
    data class Recompress(val targetWidth: Int, val targetHeight: Int, val format: ImageFormat) : ImagePlan
}

enum class ImageFormat { JPEG, PNG }

object ImageCompressionPlanner {
    const val MAX_DIMENSION = 1920
    const val SKIP_BYTES = 1L * 1024 * 1024
    const val JPEG_QUALITY = 85

    fun plan(contentType: String, sizeBytes: Long, width: Int, height: Int): ImagePlan {
        if (contentType.equals("image/gif", ignoreCase = true)) return ImagePlan.Skip
        val longSide = maxOf(width, height)
        if (sizeBytes <= SKIP_BYTES && longSide <= MAX_DIMENSION) return ImagePlan.Skip

        val scale = if (longSide > MAX_DIMENSION) MAX_DIMENSION.toDouble() / longSide else 1.0
        val format = if (contentType.equals("image/png", ignoreCase = true)) ImageFormat.PNG else ImageFormat.JPEG
        return ImagePlan.Recompress(
            targetWidth = (width * scale).toInt().coerceAtLeast(1),
            targetHeight = (height * scale).toInt().coerceAtLeast(1),
            format = format
        )
    }
}
