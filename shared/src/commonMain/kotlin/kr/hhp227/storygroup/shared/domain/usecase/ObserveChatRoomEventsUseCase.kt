package kr.hhp227.storygroup.shared.domain.usecase

import kotlinx.coroutines.flow.Flow
import kr.hhp227.storygroup.shared.domain.model.ChatEvent
import kr.hhp227.storygroup.shared.domain.repository.ChatRepository

/** 채팅방 실시간 이벤트 구독(STOMP) — 수집 취소 시 소켓도 함께 닫힌다 */
class ObserveChatRoomEventsUseCase(
    private val chatRepository: ChatRepository
) {
    operator fun invoke(chatRoomId: Long): Flow<ChatEvent> =
        chatRepository.observeRoomEvents(chatRoomId)
}
