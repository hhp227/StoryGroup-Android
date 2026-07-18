package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.repository.AuthRepository

/** 로그아웃 — 로컬 세션은 서버 성패와 무관하게 정리되므로 결과를 던지지 않는다 */
class LogoutUseCase(private val authRepository: AuthRepository) {
    suspend operator fun invoke() {
        authRepository.logout()
    }
}
