package kr.hhp227.storygroup.shared.data.network.dto

import kotlinx.serialization.Serializable

// StoryGroup-WebApp /api/groups/{groupId}/posts 계약과 1:1 (post/dto/PostDtos.kt)

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
