package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.Group
import kr.hhp227.storygroup.shared.domain.repository.GroupRepository

/** 내 그룹 목록 조회 — 실패는 예외로 던진다(LoginUseCase와 동일한 소비 규약) */
class GetMyGroupsUseCase(private val groupRepository: GroupRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(): List<Group> = groupRepository.getMyGroups().getOrThrow()
}
