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
