package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.ChatReadPosition
import kr.hhp227.storygroup.shared.domain.repository.ChatRepository

/** 방 멤버별 마지막 읽음 위치 조회 — "읽음 N" 파생의 초기 스냅숏, 이후는 READ 이벤트로 갱신한다 */
class GetChatReadPositionsUseCase(
    private val chatRepository: ChatRepository
) {
    @Throws(Exception::class)
    suspend operator fun invoke(groupId: Long?, chatRoomId: Long): List<ChatReadPosition> =
        chatRepository.getReadPositions(groupId, chatRoomId).getOrThrow()
}
