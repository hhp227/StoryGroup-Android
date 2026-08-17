package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.PublicProfile
import kr.hhp227.storygroup.shared.domain.repository.UserRepository

/** 공개 프로필 조회 — 프로필 화면 진입 시 1회 로드(친구 여부는 화면이 별도 대조) */
class GetPublicProfileUseCase(
    private val userRepository: UserRepository
) {
    @Throws(Exception::class)
    suspend operator fun invoke(userId: Long): PublicProfile =
        userRepository.getPublicProfile(userId).getOrThrow()
}
