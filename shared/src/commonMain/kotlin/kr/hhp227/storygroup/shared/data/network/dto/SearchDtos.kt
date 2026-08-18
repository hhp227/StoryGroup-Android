package kr.hhp227.storygroup.shared.data.network.dto

import kotlinx.serialization.Serializable

// 통합검색(GET /api/search) 전체 응답 — StoryGroup-WebApp search/dto/SearchDtos.kt와 1:1.
// FriendDtos.kt의 SearchResponse(친구 탭이 users만 부분 파싱)와 별개 — 이름 충돌을 피해 Unified 접두사.
// users 섹션은 기존 SearchUserResponse를 재사용한다.
@Serializable
data class UnifiedSearchResponse(
    val groups: List<GroupSearchResponse> = emptyList(),
    val posts: List<PostSearchResponse> = emptyList(),
    val files: List<FileSearchResponse> = emptyList(),
    val messages: List<MessageSearchResponse> = emptyList(),
    val users: List<SearchUserResponse> = emptyList()
)

@Serializable
data class GroupSearchResponse(
    val id: Long,
    val name: String,
    val image: String? = null,
    val description: String? = null
)

@Serializable
data class PostSearchResponse(
    val id: Long,
    val groupId: Long,
    val groupName: String,
    val authorName: String,
    val text: String,
    val createdAt: String
)

@Serializable
data class FileSearchResponse(
    val id: Long,
    val groupId: Long,
    val groupName: String,
    val name: String,
    val url: String,
    val createdAt: String
)

// 그룹 방 메시지는 groupId/groupName 있음, DM은 null
@Serializable
data class MessageSearchResponse(
    val id: Long,
    val chatRoomId: Long,
    val groupId: Long? = null,
    val groupName: String? = null,
    val authorName: String,
    val text: String,
    val createdAt: String
)
