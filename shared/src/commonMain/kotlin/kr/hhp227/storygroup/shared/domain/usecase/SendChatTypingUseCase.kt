package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.repository.ChatRepository

/**
 * 타이핑 신호 발신 — 휘발 신호라 실패/미연결이면 조용히 버려지고 예외도 없다.
 * 스로틀(2.5초)은 각 플랫폼 VM이 담당한다(웹 TYPING_SEND_INTERVAL_MS 미러).
 */
class SendChatTypingUseCase(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(chatRoomId: Long) {
        chatRepository.sendTyping(chatRoomId)
    }
}
