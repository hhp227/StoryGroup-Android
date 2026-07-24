package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.JoinGroupResult
import kr.hhp227.storygroup.shared.domain.repository.GroupRepository

/** 그룹 가입/신청 — 자동 승인 그룹이면 JOINED+가입된 그룹, 승인제면 REQUESTED+group=null */
class JoinGroupUseCase(private val groupRepository: GroupRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(groupId: Long): JoinGroupResult = groupRepository.joinGroup(groupId).getOrThrow()
}
