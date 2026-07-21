package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.repository.GroupRepository

/** 승인제 그룹 가입 신청 취소 */
class CancelJoinRequestUseCase(private val groupRepository: GroupRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(groupId: Long) {
        groupRepository.cancelJoinRequest(groupId).getOrThrow()
    }
}
