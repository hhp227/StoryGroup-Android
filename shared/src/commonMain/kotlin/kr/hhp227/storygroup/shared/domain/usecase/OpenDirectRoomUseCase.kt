package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.repository.ChatRepository

/**
 * 1:1 DM 방 get-or-create(멱등) — 방 id를 돌려준다. 방 제목은 응답이 "DM" 고정이라
 * 호출부가 이미 아는 상대 이름을 쓴다. 자기 자신은 400, 차단 관계는 403(BLOCKED).
 */
class OpenDirectRoomUseCase(
    private val chatRepository: ChatRepository
) {
    @Throws(Exception::class)
    suspend operator fun invoke(otherUserId: Long): Long =
        chatRepository.openDirectRoom(otherUserId).getOrThrow()
}
