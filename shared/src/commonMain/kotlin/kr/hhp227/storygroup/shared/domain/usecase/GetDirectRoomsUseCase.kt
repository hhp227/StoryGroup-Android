package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.DirectRoom
import kr.hhp227.storygroup.shared.domain.repository.ChatRepository

/** 채팅 허브 DM 방 목록 */
class GetDirectRoomsUseCase(
    private val chatRepository: ChatRepository
) {
    @Throws(Exception::class)
    suspend operator fun invoke(): List<DirectRoom> =
        chatRepository.getDirectRooms().getOrThrow()
}
