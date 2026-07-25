package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.repository.AuthRepository

/** 내 userId(JWT sub) — 채팅 말풍선 내/타인 정렬 판단용, 네트워크 없이 즉시 반환 */
class GetCurrentUserIdUseCase(
    private val authRepository: AuthRepository
) {
    operator fun invoke(): Long? = authRepository.currentUserId()
}
