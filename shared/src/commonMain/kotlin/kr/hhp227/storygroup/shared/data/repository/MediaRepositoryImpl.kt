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
import kr.hhp227.storygroup.shared.domain.model.ChatAttachment
import kr.hhp227.storygroup.shared.domain.repository.MediaRepository

class MediaRepositoryImpl(private val client: HttpClient) : MediaRepository {

    override suspend fun uploadImage(bytes: ByteArray, fileName: String, contentType: String): Result<String> =
        runCatching {
            client.submitFormWithBinaryData(
                url = "/api/images",
                formData = fileFormData(bytes, fileName, contentType)
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
            val uploaded = client.submitFormWithBinaryData(
                url = "/api/files",
                formData = fileFormData(bytes, fileName, contentType)
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
