package kr.hhp227.storygroup.shared.data.network.dto

import kotlinx.serialization.Serializable

// StoryGroup-WebApp /api/auth 계약과 1:1 (AuthDtos.kt)

@Serializable
data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class GoogleLoginRequest(
    val idToken: String
)

// Desktop 루프백 PKCE — 교환은 서버(/api/auth/google/code)가 한다
@Serializable
data class GoogleCodeLoginRequest(
    val code: String,
    val codeVerifier: String,
    val redirectUri: String
)

@Serializable
data class RefreshTokenRequest(
    val refreshToken: String
)

@Serializable
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long
)

@Serializable
data class UserSummaryResponse(
    val id: Long,
    val name: String,
    val email: String,
    val profileImg: String? = null
)
