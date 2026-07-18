package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.User
import kr.hhp227.storygroup.shared.domain.repository.AuthRepository

/** 가입 — 실패는 예외로 던진다(LoginUseCase와 동일한 소비 규약) */
class RegisterUseCase(private val authRepository: AuthRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(name: String, email: String, password: String): User =
        authRepository.register(name, email, password).getOrThrow()
}
