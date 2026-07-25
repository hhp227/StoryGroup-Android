package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.GroupChatRoom
import kr.hhp227.storygroup.shared.domain.repository.ChatRepository

/** 채팅 허브 그룹 채팅방 목록(라운지 제외) */
class GetGroupChatRoomsUseCase(
    private val chatRepository: ChatRepository
) {
    @Throws(Exception::class)
    suspend operator fun invoke(): List<GroupChatRoom> =
        chatRepository.getGroupChatRooms().getOrThrow()
}
