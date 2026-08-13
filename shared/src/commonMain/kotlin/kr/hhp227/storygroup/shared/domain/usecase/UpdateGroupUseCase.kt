package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.Group
import kr.hhp227.storygroup.shared.domain.model.GroupJoinType
import kr.hhp227.storygroup.shared.domain.repository.GroupRepository

/**
 * 그룹 정보 수정(OWNER 전용) — ⚠️전체 교체 계약이라 폼이 로드해 온 기존 값을 항상 실어 보낸다.
 * joinType null=기존 유지(라운지 폼이 가입 방식을 안 보낼 때).
 */
class UpdateGroupUseCase(private val groupRepository: GroupRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(
        groupId: Long,
        name: String,
        description: String?,
        image: String?,
        joinType: GroupJoinType?
    ): Group = groupRepository.updateGroup(groupId, name, description, image, joinType).getOrThrow()
}
