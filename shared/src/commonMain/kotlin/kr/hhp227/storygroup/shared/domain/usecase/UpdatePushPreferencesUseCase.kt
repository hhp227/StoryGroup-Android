package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.repository.UserRepository

/** 푸시 종류별 on/off 저장 — 토글 즉시 호출(전체 교체라 두 값 모두 넘긴다), 실패 시 화면이 롤백 */
class UpdatePushPreferencesUseCase(private val userRepository: UserRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(chatEnabled: Boolean, activityEnabled: Boolean) {
        userRepository.updatePushPreferences(chatEnabled, activityEnabled).getOrThrow()
    }
}
