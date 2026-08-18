package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.Friend
import kr.hhp227.storygroup.shared.domain.repository.FriendRepository

/** 내 친구 목록 — 페이징 없이 전량, 서버가 이름순 정렬(웹 listFriends 미러) */
class GetFriendsUseCase(
    private val friendRepository: FriendRepository
) {
    @Throws(Exception::class)
    suspend operator fun invoke(): List<Friend> = friendRepository.getFriends().getOrThrow()
}
