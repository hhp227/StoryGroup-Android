package kr.hhp227.storygroup.shared.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.append
import kr.hhp227.storygroup.shared.data.network.dto.UploadedFileResponse
import kr.hhp227.storygroup.shared.data.network.dto.UploadedImageResponse
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

    override suspend fun uploadFile(bytes: ByteArray, fileName: String, contentType: String): Result<ChatAttachment> =
        runCatching {
            val uploaded = client.submitFormWithBinaryData(
                url = "/api/files",
                formData = fileFormData(bytes, fileName, contentType)
            ).body<UploadedFileResponse>()
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
}
