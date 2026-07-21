package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.Group
import kr.hhp227.storygroup.shared.domain.model.GroupJoinType
import kr.hhp227.storygroup.shared.domain.repository.GroupRepository

/** 그룹 생성 — 실패는 예외로 던진다(CreatePostUseCase와 동일한 소비 규약) */
class CreateGroupUseCase(private val groupRepository: GroupRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(
        name: String,
        description: String?,
        image: String?,
        joinType: GroupJoinType
    ): Group = groupRepository.createGroup(name, description, image, joinType).getOrThrow()
}
