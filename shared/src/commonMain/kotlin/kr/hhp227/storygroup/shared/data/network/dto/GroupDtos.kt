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

// 4xx 공통 에러 본문 — common/exception/GlobalExceptionHandler.kt ErrorResponse와 1:1
@Serializable
data class ErrorResponse(
    val code: String? = null,
    val message: String? = null
)
