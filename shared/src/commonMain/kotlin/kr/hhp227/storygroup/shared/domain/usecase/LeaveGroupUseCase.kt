package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.repository.GroupRepository

/** 그룹 나가기(멤버/부방장) — OWNER는 서버가 거부("그룹 삭제를 이용하세요"), 라운지도 거부 */
class LeaveGroupUseCase(private val groupRepository: GroupRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(groupId: Long) {
        groupRepository.leaveGroup(groupId).getOrThrow()
    }
}
