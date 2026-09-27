package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.AuthTokens
import kr.hhp227.storygroup.shared.domain.repository.AuthRepository

/**
 * 애플 로그인 — iOS는 identityToken(+최초 이름), Android·Desktop은 백엔드 콜백 코드+verifier.
 * ObjC에 Kotlin 기본 인자가 안 보이므로 Swift는 모든 인자를 넘긴다(nil 허용)
 */
class LoginWithAppleUseCase(private val authRepository: AuthRepository) {
    @Throws(Exception::class)
    suspend fun withIdentityToken(
        identityToken: String,
        authorizationCode: String?,
        clientType: String,
        firstName: String?,
        lastName: String?
    ): AuthTokens =
        authRepository.loginWithApple(identityToken, authorizationCode, clientType, firstName, lastName).getOrThrow()

    @Throws(Exception::class)
    suspend fun withExchangeCode(code: String, verifier: String): AuthTokens =
        authRepository.exchangeAppleCode(code, verifier).getOrThrow()
}
