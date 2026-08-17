package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.repository.FriendRepository

/** 친구 해제 — 단방향 등록의 반대 방향(상대 목록에는 영향 없음). 등록 내역이 없으면 404 */
class RemoveFriendUseCase(
    private val friendRepository: FriendRepository
) {
    @Throws(Exception::class)
    suspend operator fun invoke(userId: Long) {
        friendRepository.removeFriend(userId).getOrThrow()
    }
}
