package kr.hhp227.storygroup.shared.data.network.dto

import kotlinx.serialization.Serializable

// StoryGroup-WebApp /api/images·/api/videos·/api/files 계약과 1:1 (common/storage/*UploadController.kt)

@Serializable
data class UploadedImageResponse(val url: String)

/** POST /api/videos 응답 — /api/images와 같은 모양(url만)이라 게시글 videos에 그대로 싣는다 */
@Serializable
data class UploadedVideoResponse(val url: String)

/** POST /api/files 응답 — MessageAttachmentPayload와 필드가 같아 채팅 첨부에 그대로 실린다 */
@Serializable
data class UploadedFileResponse(
    val url: String,
    val name: String? = null,
    val contentType: String? = null,
    val size: Long? = null
)
