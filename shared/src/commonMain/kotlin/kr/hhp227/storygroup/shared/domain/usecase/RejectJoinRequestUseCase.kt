package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.repository.GroupRepository

/** 가입 신청 거절(모더레이터 전용) — 신청자는 다시 신청할 수 있다 */
class RejectJoinRequestUseCase(private val groupRepository: GroupRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(groupId: Long, userId: Long) {
        groupRepository.rejectJoinRequest(groupId, userId).getOrThrow()
    }
}
