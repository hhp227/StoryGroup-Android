package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.RtcRoom
import kr.hhp227.storygroup.shared.domain.model.RtcSignalType
import kr.hhp227.storygroup.shared.domain.repository.RtcRepository

/**
 * SDP/ICE 시그널 발신(회의/DM 공용) — 타이핑과 같은 휘발 신호라 실패/미연결이면 조용히 버려지고
 * 예외도 없다. 서버도 수신자가 같은 rtc 방에 없으면 조용히 버린다(PEERS에 나타난 상대에게만 보낼 것).
 */
class SendRtcSignalUseCase(
    private val rtcRepository: RtcRepository
) {
    @Throws(Exception::class)
    suspend operator fun invoke(room: RtcRoom, type: RtcSignalType, toUserId: Long, payload: String) {
        rtcRepository.sendSignal(room, type, toUserId, payload)
    }
}
