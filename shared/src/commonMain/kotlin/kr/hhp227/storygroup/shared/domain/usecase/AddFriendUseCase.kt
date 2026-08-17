package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.repository.FriendRepository

/** 친구 등록(단방향, 승인 절차 없음) — 자기 자신은 400, 이미 등록은 409(서버 문구 노출) */
class AddFriendUseCase(
    private val friendRepository: FriendRepository
) {
    @Throws(Exception::class)
    suspend operator fun invoke(userId: Long) {
        friendRepository.addFriend(userId).getOrThrow()
    }
}
