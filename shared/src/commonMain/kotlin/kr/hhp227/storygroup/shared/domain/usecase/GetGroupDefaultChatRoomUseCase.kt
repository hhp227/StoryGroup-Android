package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.repository.ChatRepository

/**
 * 그룹 기본 채팅방 id — 그룹 생성 시 자동으로 만들어져 항상 가장 먼저 생성된 방이다
 * (웹 /groups/[id]/chat의 기본 선택 미러). 방 제목은 허브처럼 그룹명을 쓴다.
 */
class GetGroupDefaultChatRoomUseCase(
    private val chatRepository: ChatRepository
) {
    @Throws(Exception::class)
    suspend operator fun invoke(groupId: Long): Long? =
        chatRepository.getGroupDefaultChatRoom(groupId).getOrThrow()
}
