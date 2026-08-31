package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.PushPreferences
import kr.hhp227.storygroup.shared.domain.repository.UserRepository

/** 푸시 종류별 on/off 조회 — 앱 설정 화면 진입 시 1회 */
class GetPushPreferencesUseCase(private val userRepository: UserRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(): PushPreferences = userRepository.getPushPreferences().getOrThrow()
}
