package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.ChatMessage
import kr.hhp227.storygroup.shared.domain.repository.ChatRepository

/** 텍스트 메시지 전송(REST) — 생성된 메시지를 반환하고, 서버가 방 토픽에 MESSAGE_CREATED를 브로드캐스트한다 */
class SendChatMessageUseCase(
    private val chatRepository: ChatRepository
) {
    @Throws(Exception::class)
    suspend operator fun invoke(groupId: Long?, chatRoomId: Long, text: String): ChatMessage =
        chatRepository.sendMessage(groupId, chatRoomId, text).getOrThrow()
}
