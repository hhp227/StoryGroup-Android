package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.ChatMessage
import kr.hhp227.storygroup.shared.domain.repository.ChatRepository

/** 메시지 이력 — 최신순(DESC) 오프셋 페이징(page 0=가장 최근, size 서버 상한 50). groupId null이면 DM */
class GetChatMessagesUseCase(
    private val chatRepository: ChatRepository
) {
    @Throws(Exception::class)
    suspend operator fun invoke(groupId: Long?, chatRoomId: Long, page: Int, size: Int): List<ChatMessage> =
        chatRepository.getMessages(groupId, chatRoomId, page, size).getOrThrow()
}
