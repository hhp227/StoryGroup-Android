package kr.hhp227.storygroup.shared.domain.model

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String
)
