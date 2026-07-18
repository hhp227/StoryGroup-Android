package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.AuthTokens
import kr.hhp227.storygroup.shared.domain.repository.AuthRepository

/**
 * 실패는 예외로 던진다 — Swift에는 async throws로 노출되어
 * Compose ViewModel(runCatching)과 SwiftUI ViewModel(do/catch)이 같은 로직으로 소비한다.
 */
class LoginUseCase(private val authRepository: AuthRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(email: String, password: String): AuthTokens =
        authRepository.login(email, password).getOrThrow()
}
