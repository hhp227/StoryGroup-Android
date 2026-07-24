package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.GroupJoinRequest
import kr.hhp227.storygroup.shared.domain.repository.GroupRepository

/** 승인 대기 가입 신청 목록 조회(모더레이터 전용) — 실패는 예외로 던진다(LoginUseCase와 동일한 소비 규약) */
class GetJoinRequestsUseCase(private val groupRepository: GroupRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(groupId: Long): List<GroupJoinRequest> =
        groupRepository.getJoinRequests(groupId).getOrThrow()
}
