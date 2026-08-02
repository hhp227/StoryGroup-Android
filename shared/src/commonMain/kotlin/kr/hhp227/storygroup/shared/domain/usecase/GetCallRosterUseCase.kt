package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.RtcCallPeer
import kr.hhp227.storygroup.shared.domain.repository.RtcRepository

/**
 * 통화 로스터 스냅숏 — 입장(구독) 없이 "지금 이 방에서 통화 중인 사람"을 본다
 * (웹 라이브 카드 미러). 채팅방 라이브 바("N명 통화 중 — 참가")가 주기 폴링한다.
 */
class GetCallRosterUseCase(
    private val rtcRepository: RtcRepository
) {
    @Throws(Exception::class)
    suspend operator fun invoke(chatRoomId: Long): List<RtcCallPeer> =
        rtcRepository.getCallRoster(chatRoomId).getOrThrow()
}
