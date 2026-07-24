package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.Profile
import kr.hhp227.storygroup.shared.domain.repository.UserRepository

/**
 * 프로필 수정 — 실패는 예외로 던진다(LoginUseCase와 동일한 소비 규약).
 * PATCH는 전체 교체 계약이라 수정하지 않는 필드(profileImg 등)도 기존 값을 그대로 넘겨야 한다.
 */
class UpdateMyProfileUseCase(private val userRepository: UserRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(name: String, profileImg: String?, bio: String?, statusMessage: String?): Profile =
        userRepository.updateMyProfile(name, profileImg, bio, statusMessage).getOrThrow()
}
