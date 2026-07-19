package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.GroupMember
import kr.hhp227.storygroup.shared.domain.repository.GroupRepository

/** 그룹 멤버 목록 조회 — 실패는 예외로 던진다(LoginUseCase와 동일한 소비 규약) */
class GetGroupMembersUseCase(private val groupRepository: GroupRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(groupId: Long): List<GroupMember> =
        groupRepository.getMembers(groupId).getOrThrow()
}
