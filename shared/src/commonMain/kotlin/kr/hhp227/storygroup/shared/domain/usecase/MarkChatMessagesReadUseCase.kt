package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.repository.ChatRepository

/** 읽음 위치 보고 — 서버가 GREATEST로 단조 증가를 보장하므로 낡은 값 재전송도 안전하다 */
class MarkChatMessagesReadUseCase(
    private val chatRepository: ChatRepository
) {
    @Throws(Exception::class)
    suspend operator fun invoke(groupId: Long?, chatRoomId: Long, lastReadMessageId: Long) {
        chatRepository.markRead(groupId, chatRoomId, lastReadMessageId).getOrThrow()
    }
}
