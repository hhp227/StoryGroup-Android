package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.repository.UserRepository

/**
 * 차단 해제 — 설정의 차단 사용자 관리 화면 전용(웹 /settings/blocked 미러).
 * 해제해도 이미 걸러낸 피드 스냅샷은 되돌리지 않는다 — 다음 새로고침부터 다시 보인다.
 */
class UnblockUserUseCase(private val userRepository: UserRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(userId: Long) {
        userRepository.unblockUser(userId).getOrThrow()
    }
}
