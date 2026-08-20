package kr.hhp227.storygroup.ui.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kr.hhp227.storygroup.shared.domain.media.CompressedImage
import kr.hhp227.storygroup.shared.domain.media.ImageCompressionPlanner
import kr.hhp227.storygroup.shared.domain.media.ImageCompressor
import kr.hhp227.storygroup.shared.domain.media.ImageFormat
import kr.hhp227.storygroup.shared.domain.media.ImagePlan
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/**
 * Desktop 이미지 압축(§3·§9) — ImageIO 디코드→스케일→JPEG(q0.85)/PNG 인코드.
 * ImageIO가 못 읽는 포맷(HEIC 등)은 원본 폴백(§3-3) — 예외를 밖으로 내보내지 않는다.
 */
class JvmImageCompressor : ImageCompressor {

    override suspend fun compress(bytes: ByteArray, contentType: String): CompressedImage =
        withContext(Dispatchers.IO) {
            val original = CompressedImage(bytes, contentType)
            runCatching {
                val decoded = ImageIO.read(ByteArrayInputStream(bytes)) ?: return@runCatching original
                val plan = ImageCompressionPlanner.plan(contentType, bytes.size.toLong(), decoded.width, decoded.height)
                val recompress = plan as? ImagePlan.Recompress ?: return@runCatching original
                val isPng = recompress.format == ImageFormat.PNG
                // PNG는 투명도 보존을 위해 ARGB, JPEG는 알파 없는 RGB 캔버스에 그린다
                val scaled = BufferedImage(
                    recompress.targetWidth,
                    recompress.targetHeight,
                    if (isPng) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
                )
                scaled.createGraphics().apply {
                    drawImage(
                        decoded.getScaledInstance(recompress.targetWidth, recompress.targetHeight, Image.SCALE_SMOOTH),
                        0, 0, null
                    )
                    dispose()
                }
                val out = ByteArrayOutputStream()
                if (isPng) {
                    ImageIO.write(scaled, "png", out)
                    CompressedImage(out.toByteArray(), "image/png")
                } else {
                    val writer = ImageIO.getImageWritersByFormatName("jpg").next()
                    val param = writer.defaultWriteParam.apply {
                        compressionMode = ImageWriteParam.MODE_EXPLICIT
                        compressionQuality = ImageCompressionPlanner.JPEG_QUALITY / 100f
                    }
                    ImageIO.createImageOutputStream(out).use { stream ->
                        writer.output = stream
                        writer.write(null, IIOImage(scaled, null, null), param)
                    }
                    writer.dispose()
                    CompressedImage(out.toByteArray(), "image/jpeg")
                }
            }.getOrDefault(original)
        }
}
