package kr.hhp227.storygroup.shared.data.network.dto

import kotlinx.serialization.Serializable

// StoryGroup-WebApp /api/groups 계약과 1:1 (group/dto/GroupDtos.kt GroupResponse)

@Serializable
data class GroupResponse(
    val id: Long,
    val name: String,
    val description: String? = null,
    val image: String? = null,
    val joinType: String,
    val myRole: String,
    val createdAt: String,
    val isLounge: Boolean = false
)

// StoryGroup-WebApp /api/groups/{id}/members 계약과 1:1 (group/dto/GroupDtos.kt MemberResponse)
@Serializable
data class MemberResponse(
    val userId: Long,
    val name: String,
    val profileImg: String? = null,
    val role: String,
    val joinedAt: String
)

// POST /api/groups 요청 본문 — group/dto/GroupDtos.kt CreateGroupRequest와 1:1
@Serializable
data class CreateGroupRequest(
    val name: String,
    val description: String? = null,
    val image: String? = null,
    val joinType: String = "AUTO_APPROVE"
)

// PATCH /api/groups/{id} 요청 본문 — group/dto/GroupDtos.kt UpdateGroupRequest와 1:1.
// ⚠️name/description/image는 전체 교체 계약(null=null로 덮어씀) — 호출부가 기존 값을 실어 보낸다.
// joinType만 null=기존 유지(배포 전 클라이언트 하위호환용 서버 시맨틱).
@Serializable
data class UpdateGroupRequest(
    val name: String,
    val description: String? = null,
    val image: String? = null,
    val joinType: String? = null
)

// GET /api/groups/discover 응답 — group/dto/GroupDtos.kt DiscoverGroupResponse와 1:1
@Serializable
data class DiscoverGroupResponse(
    val id: Long,
    val name: String,
    val description: String? = null,
    val image: String? = null,
    val joinType: String,
    val memberCount: Long,
    val membership: String,
    val createdAt: String
)

// POST /api/groups/{id}/join 응답 — group/dto/GroupDtos.kt JoinGroupResponse와 1:1
@Serializable
data class JoinGroupResponse(
    val status: String,
    val group: GroupResponse? = null
)

// GET /api/groups/{id}/join-requests 응답 — group/dto/GroupDtos.kt JoinRequestResponse와 1:1
@Serializable
data class JoinRequestResponse(
    val userId: Long,
    val name: String,
    val profileImg: String? = null,
    val requestedAt: String
)

// POST /api/groups/{id}/invites 요청 본문 — group/dto/GroupDtos.kt CreateInviteRequest와 1:1(null=무제한/무기한)
@Serializable
data class CreateInviteRequest(
    val maxUses: Int? = null,
    val expiresInDays: Int? = null
)

// POST /api/groups/{id}/invites 응답 — group/dto/GroupDtos.kt InviteResponse와 1:1
@Serializable
data class InviteResponse(
    val code: String,
    val maxUses: Int? = null,
    val expiresAt: String? = null
)

// GET /api/groups/{id}/photos 응답 — 웹 lib/api.ts GroupPhoto/GroupPhotosPage와 1:1.
// 다른 목록과 달리 {totalCount, photos} 오브젝트다 — totalCount는 앱에선 미사용(탭 구조라 "N장" 표기 없음)
@Serializable
data class GroupPhotoResponse(
    val id: Long,
    val postId: Long,
    val image: String,
    val mediaType: String = "image",
    val userId: Long,
    val authorName: String,
    val createdAt: String
)

@Serializable
data class GroupPhotosPageResponse(
    val totalCount: Long = 0,
    val photos: List<GroupPhotoResponse> = emptyList()
)

// 4xx 공통 에러 본문 — common/exception/GlobalExceptionHandler.kt ErrorResponse와 1:1
@Serializable
data class ErrorResponse(
    val code: String? = null,
    val message: String? = null
)
