package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.repository.UserRepository

/**
 * 회원 탈퇴 — 비밀번호 계정은 password, 구글 전용 계정은 confirmText("탈퇴").
 * 성공 시 호출측이 기존 로그아웃 플로우로 세션을 정리한다(설계 §4)
 */
class DeleteAccountUseCase(private val userRepository: UserRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(password: String?, confirmText: String?) {
        userRepository.deleteAccount(password, confirmText).getOrThrow()
    }
}
