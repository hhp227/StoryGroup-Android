package kr.hhp227.storygroup.shared.data.source

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

/**
 * 미디어 원격 소스 — 전송·DTO만 담당, 예외는 그대로 던진다(Result 래핑은 리포지토리 몫).
 * 이미지 압축 판단(§4, ImageCompressor 호출 여부·GIF 우회)은 리포지토리에 남아있다 —
 * 여기는 바이트를 그대로 멀티파트로 실어 보내기만 한다.
 */
interface MediaRemoteDataSource {
    suspend fun uploadImage(bytes: ByteArray, fileName: String, contentType: String): UploadedImageResponse
    suspend fun uploadVideo(bytes: ByteArray, fileName: String, contentType: String): UploadedVideoResponse
    suspend fun uploadFile(bytes: ByteArray, fileName: String, contentType: String): UploadedFileResponse
}

class MediaRemoteDataSourceImpl(private val client: HttpClient) : MediaRemoteDataSource {
    override suspend fun uploadImage(bytes: ByteArray, fileName: String, contentType: String): UploadedImageResponse =
        client.submitFormWithBinaryData(
            url = "/api/images",
            formData = fileFormData(bytes, fileName, contentType)
        ).body()

    override suspend fun uploadVideo(bytes: ByteArray, fileName: String, contentType: String): UploadedVideoResponse =
        client.submitFormWithBinaryData(
            url = "/api/videos",
            formData = fileFormData(bytes, fileName, contentType)
        ) {
            uploadTimeout()
        }.body()

    override suspend fun uploadFile(bytes: ByteArray, fileName: String, contentType: String): UploadedFileResponse =
        client.submitFormWithBinaryData(
            url = "/api/files",
            formData = fileFormData(bytes, fileName, contentType)
        ) {
            uploadTimeout()
        }.body()

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
