package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.repository.GroupRepository

/** 가입 신청 승인(모더레이터 전용) — 승인된 사용자는 즉시 멤버가 된다 */
class ApproveJoinRequestUseCase(private val groupRepository: GroupRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(groupId: Long, userId: Long) {
        groupRepository.approveJoinRequest(groupId, userId).getOrThrow()
    }
}
