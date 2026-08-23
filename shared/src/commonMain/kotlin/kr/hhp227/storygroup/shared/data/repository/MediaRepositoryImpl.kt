package kr.hhp227.storygroup.shared.data.repository

import kr.hhp227.storygroup.shared.data.source.MediaRemoteDataSource
import kr.hhp227.storygroup.shared.domain.media.ImageCompressor
import kr.hhp227.storygroup.shared.domain.model.ChatAttachment
import kr.hhp227.storygroup.shared.domain.repository.MediaRepository

class MediaRepositoryImpl(
    private val mediaRemoteDataSource: MediaRemoteDataSource,
    // 이미지 투명 압축(§4) — null이면 무압축(플랫폼 실행기 주입 전과 동일 동작)
    private val imageCompressor: ImageCompressor? = null
) : MediaRepository {

    override suspend fun uploadImage(bytes: ByteArray, fileName: String, contentType: String): Result<String> =
        runCatching {
            val (b, ct, fn) = maybeCompressImage(bytes, fileName, contentType)
            mediaRemoteDataSource.uploadImage(b, fn, ct).url
        }

    override suspend fun uploadVideo(bytes: ByteArray, fileName: String, contentType: String): Result<String> =
        runCatching {
            mediaRemoteDataSource.uploadVideo(bytes, fileName, contentType).url
        }

    override suspend fun uploadFile(bytes: ByteArray, fileName: String, contentType: String): Result<ChatAttachment> =
        runCatching {
            // 채팅 이미지도 이 엔드포인트로 온다(§4) — 이미지 MIME이면 같은 투명 압축을 거친다
            val (b, ct, fn) = maybeCompressImage(bytes, fileName, contentType)
            val uploaded = mediaRemoteDataSource.uploadFile(b, fn, ct)

            ChatAttachment(
                url = uploaded.url,
                name = uploaded.name,
                contentType = uploaded.contentType,
                size = uploaded.size
            )
        }

    /**
     * 이미지 MIME일 때만 압축(GIF 제외 — 재인코딩하면 애니메이션이 깨진다).
     * 압축기가 예외를 던져도 원본으로 폴백 — 업로드 자체를 압축 실패로 막지 않는다.
     * contentType이 바뀌면 확장자도 맞춘다(서버가 저장 이름을 확장자로 다시 만든다).
     */
    private suspend fun maybeCompressImage(
        bytes: ByteArray,
        fileName: String,
        contentType: String
    ): Triple<ByteArray, String, String> {
        val compressor = imageCompressor
            ?: return Triple(bytes, contentType, fileName)
        if (!contentType.startsWith("image/") || contentType.equals("image/gif", ignoreCase = true)) {
            return Triple(bytes, contentType, fileName)
        }
        val out = runCatching { compressor.compress(bytes, contentType) }.getOrNull()
            ?: return Triple(bytes, contentType, fileName)
        val extension = if (out.contentType.equals("image/png", ignoreCase = true)) "png" else "jpg"
        val newName = fileName.substringBeforeLast('.') + "." + extension
        return Triple(out.bytes, out.contentType, newName)
    }
}
