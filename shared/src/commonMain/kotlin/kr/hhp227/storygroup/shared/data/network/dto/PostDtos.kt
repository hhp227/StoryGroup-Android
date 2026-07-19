package kr.hhp227.storygroup.shared.data.network.dto

import kotlinx.serialization.Serializable

// StoryGroup-WebApp /api/groups/{groupId}/posts 계약과 1:1 (post/dto/PostDtos.kt)

// text에 공백 검증이 없는 것은 첨부만 있는 게시글을 허용하는 백엔드 규칙 미러 —
// 앱 MVP는 텍스트만 보내므로 "본문 필수" 검증은 각 플랫폼 ViewModel이 한다.
@Serializable
data class CreatePostRequest(
    val text: String,
    val images: List<String>? = null,
    val videos: List<String>? = null
)

@Serializable
data class PostResponse(
    val id: Long,
    val groupId: Long,
    val userId: Long,
    val authorName: String,
    val authorProfileImg: String? = null,
    val text: String,
    val images: List<ImageResponse> = emptyList(),
    // 웹과 동일한 방어 — videos를 모르는 배포 전 백엔드는 이 필드를 안 내려준다
    val videos: List<VideoResponse> = emptyList(),
    val isNotice: Boolean = false,
    val createdAt: String
)

@Serializable
data class ImageResponse(
    val id: Long,
    val image: String
)

@Serializable
data class VideoResponse(
    val id: Long,
    val video: String
)
