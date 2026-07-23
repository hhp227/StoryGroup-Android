package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.GroupInvite
import kr.hhp227.storygroup.shared.domain.repository.GroupRepository

/** 초대코드 생성(모더레이터 전용) — null 제한은 무제한/무기한, 코드를 아는 사람은 승인 없이 바로 가입된다 */
class CreateGroupInviteUseCase(private val groupRepository: GroupRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(groupId: Long, maxUses: Int?, expiresInDays: Int?): GroupInvite =
        groupRepository.createInvite(groupId, maxUses, expiresInDays).getOrThrow()
}
