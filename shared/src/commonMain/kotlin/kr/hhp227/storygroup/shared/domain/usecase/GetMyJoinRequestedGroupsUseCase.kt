package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.DiscoverGroup
import kr.hhp227.storygroup.shared.domain.repository.GroupRepository

/** 내가 가입 신청중(PENDING)인 그룹 목록 조회 — 실패는 예외로 던진다(GetMyGroupsUseCase와 동일한 소비 규약) */
class GetMyJoinRequestedGroupsUseCase(private val groupRepository: GroupRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(): List<DiscoverGroup> = groupRepository.getMyJoinRequestedGroups().getOrThrow()
}
