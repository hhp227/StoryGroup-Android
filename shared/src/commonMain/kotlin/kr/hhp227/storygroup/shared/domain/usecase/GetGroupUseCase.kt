package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.Group
import kr.hhp227.storygroup.shared.domain.repository.GroupRepository

/** 그룹 단건 조회(상세 진입 시 신선화) — 실패는 예외로 던진다(LoginUseCase와 동일한 소비 규약) */
class GetGroupUseCase(private val groupRepository: GroupRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(groupId: Long): Group = groupRepository.getGroup(groupId).getOrThrow()
}
