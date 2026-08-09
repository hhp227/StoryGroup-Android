package kr.hhp227.storygroup.shared.data.network.dto

import kotlinx.serialization.Serializable

// StoryGroup-WebApp /api/users/me 계약과 1:1 (user/dto/UserDtos.kt ProfileResponse)

@Serializable
data class ProfileResponse(
    val id: Long,
    val name: String,
    val email: String,
    val profileImg: String? = null,
    val bio: String? = null,
    val statusMessage: String? = null,
    val isAdmin: Boolean = false
)

// PATCH는 부분 갱신이 아니라 전체 교체 — null을 보내면 해당 필드가 지워지므로
// 수정하지 않는 필드도 기존 값을 그대로 실어 보내야 한다
@Serializable
data class UpdateProfileRequest(
    val name: String,
    val profileImg: String? = null,
    val bio: String? = null,
    val statusMessage: String? = null
)

@Serializable
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)

@Serializable
data class BlockedUserResponse(
    val userId: Long,
    val name: String,
    val profileImg: String? = null,
    val blockedAt: String = ""
)

