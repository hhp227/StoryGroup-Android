package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.Group
import kr.hhp227.storygroup.shared.domain.repository.GroupRepository

/** 초대코드로 가입 — 승인제 그룹이라도 즉시 MEMBER로 가입되고, 가입된 그룹을 돌려준다 */
class JoinGroupByCodeUseCase(private val groupRepository: GroupRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(code: String): Group = groupRepository.joinByCode(code).getOrThrow()
}
