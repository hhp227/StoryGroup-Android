package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.repository.GroupRepository

/** 그룹 삭제(OWNER 전용, 라운지 불가) — 게시글·채팅·파일이 모두 사라진다(웹 위험 구역 미러) */
class DeleteGroupUseCase(private val groupRepository: GroupRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(groupId: Long) {
        groupRepository.deleteGroup(groupId).getOrThrow()
    }
}
