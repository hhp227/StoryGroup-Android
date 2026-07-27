package kr.hhp227.storygroup.shared.data.network.dto

import kotlinx.serialization.Serializable

// StoryGroup-WebApp /api/images·/api/files 계약과 1:1 (common/storage/*UploadController.kt)

@Serializable
data class UploadedImageResponse(val url: String)

/** POST /api/files 응답 — MessageAttachmentPayload와 필드가 같아 채팅 첨부에 그대로 실린다 */
@Serializable
data class UploadedFileResponse(
    val url: String,
    val name: String? = null,
    val contentType: String? = null,
    val size: Long? = null
)
