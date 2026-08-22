package kr.hhp227.storygroup.shared.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.append
import kr.hhp227.storygroup.shared.data.network.dto.UploadedFileResponse
import kr.hhp227.storygroup.shared.data.network.dto.UploadedImageResponse
import kr.hhp227.storygroup.shared.data.network.dto.UploadedVideoResponse
import kr.hhp227.storygroup.shared.domain.media.ImageCompressor
import kr.hhp227.storygroup.shared.domain.model.ChatAttachment
import kr.hhp227.storygroup.shared.domain.repository.MediaRepository

class MediaRepositoryImpl(
    private val client: HttpClient,
    // 이미지 투명 압축(§4) — null이면 무압축(플랫폼 실행기 주입 전과 동일 동작)
    private val imageCompressor: ImageCompressor? = null
) : MediaRepository {

    override suspend fun uploadImage(bytes: ByteArray, fileName: String, contentType: String): Result<String> =
        runCatching {
            val (b, ct, fn) = maybeCompressImage(bytes, fileName, contentType)
            client.submitFormWithBinaryData(
                url = "/api/images",
                formData = fileFormData(b, fn, ct)
            ).body<UploadedImageResponse>().url
        }

    override suspend fun uploadVideo(bytes: ByteArray, fileName: String, contentType: String): Result<String> =
        runCatching {
            client.submitFormWithBinaryData(
                url = "/api/videos",
                formData = fileFormData(bytes, fileName, contentType)
            ) {
                uploadTimeout()
            }.body<UploadedVideoResponse>().url
        }

    override suspend fun uploadFile(bytes: ByteArray, fileName: String, contentType: String): Result<ChatAttachment> =
        runCatching {
            // 채팅 이미지도 이 엔드포인트로 온다(§4) — 이미지 MIME이면 같은 투명 압축을 거친다
            val (b, ct, fn) = maybeCompressImage(bytes, fileName, contentType)
            val uploaded = client.submitFormWithBinaryData(
                url = "/api/files",
                formData = fileFormData(b, fn, ct)
            ) {
                uploadTimeout()
            }.body<UploadedFileResponse>()
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

    private fun fileFormData(bytes: ByteArray, fileName: String, contentType: String) =
        formData {
            append("file", bytes, Headers.build {
                append(HttpHeaders.ContentType, contentType)
                append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
            })
        }

    /**
     * 큰 첨부를 올리는 요청만 타임아웃을 늘린다 — 전역 30초(ApiClient)는 요청 전체에 걸리는 값이라
     * 바디 전송 시간까지 그 안에 들어가야 한다. 10MB 동영상이면 상향 2.7Mbps가 계속 나와야 하고
     * 채팅 첨부는 20MB까지 허용돼 더 빠듯하다 — 콜드스타트까지 겹치면 자주 터진다.
     * 전역 값은 그대로 둔다(조회 요청이 30초 넘게 매달리지 않게 하려고 넣은 값이다).
     */
    private fun HttpRequestBuilder.uploadTimeout() {
        timeout {
            requestTimeoutMillis = UPLOAD_TIMEOUT_MILLIS
            socketTimeoutMillis = UPLOAD_TIMEOUT_MILLIS
        }
    }

    private companion object {
        const val UPLOAD_TIMEOUT_MILLIS = 180_000L
    }
}
