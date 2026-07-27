package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.ChatAttachment
import kr.hhp227.storygroup.shared.domain.model.ChatMessage
import kr.hhp227.storygroup.shared.domain.repository.ChatRepository

/**
 * 메시지 전송(REST) — 생성된 메시지를 반환하고, 서버가 방 토픽에 MESSAGE_CREATED를 브로드캐스트한다.
 * 첨부는 UploadChatFileUseCase 결과를 그대로 싣는다(전송 시점 업로드 — 웹 미러). text·attachment 둘 다 비면 400.
 */
class SendChatMessageUseCase(
    private val chatRepository: ChatRepository
) {
    @Throws(Exception::class)
    suspend operator fun invoke(
        groupId: Long?,
        chatRoomId: Long,
        text: String,
        attachment: ChatAttachment? = null
    ): ChatMessage =
        chatRepository.sendMessage(groupId, chatRoomId, text, attachment).getOrThrow()
}
