package kr.hhp227.storygroup.shared.data.network.dto

import kotlinx.serialization.Serializable

// StoryGroup-WebApp /api/users/me/friends 계약과 1:1 (friend/dto/FriendDtos.kt FriendResponse)

@Serializable
data class FriendResponse(
    val userId: Long,
    val name: String,
    val profileImg: String? = null,
    val statusMessage: String? = null,
    val friendedAt: String = "",
    val online: Boolean = false
)

// 통합검색(GET /api/search) 응답 — 친구 탭은 users 섹션만 쓴다
// (groups/posts/files/messages 4섹션은 ignoreUnknownKeys가 무시 — 홈 통합검색 때 별도 확장)
@Serializable
data class SearchResponse(
    val users: List<SearchUserResponse> = emptyList()
)

@Serializable
data class SearchUserResponse(
    val id: Long,
    val name: String,
    val profileImg: String? = null,
    val statusMessage: String? = null
)
