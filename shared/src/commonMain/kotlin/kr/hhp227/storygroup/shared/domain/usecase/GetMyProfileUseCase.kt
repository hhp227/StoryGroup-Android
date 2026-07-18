package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.Profile
import kr.hhp227.storygroup.shared.domain.repository.UserRepository

/** 내 정보 조회 — 실패는 예외로 던진다(LoginUseCase와 동일한 소비 규약) */
class GetMyProfileUseCase(private val userRepository: UserRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(): Profile = userRepository.getMyProfile().getOrThrow()
}
