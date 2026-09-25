package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.AuthTokens
import kr.hhp227.storygroup.shared.domain.repository.AuthRepository

/**
 * 구글 로그인 — Android·iOS는 ID 토큰, Desktop은 루프백 PKCE 인가 코드(서버가 교환).
 * 실패는 예외(LoginUseCase와 같은 계약 — Swift async throws)
 */
class LoginWithGoogleUseCase(private val authRepository: AuthRepository) {
    @Throws(Exception::class)
    suspend fun withIdToken(idToken: String): AuthTokens =
        authRepository.loginWithGoogle(idToken).getOrThrow()

    @Throws(Exception::class)
    suspend fun withAuthCode(code: String, codeVerifier: String, redirectUri: String): AuthTokens =
        authRepository.loginWithGoogleCode(code, codeVerifier, redirectUri).getOrThrow()
}
