package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.repository.AuthRepository

/** 저장된 세션 존재 여부 — 앱 시작 시 초기 라우팅에 쓴다 */
class IsLoggedInUseCase(private val authRepository: AuthRepository) {
    operator fun invoke(): Boolean = authRepository.isLoggedIn()
}
