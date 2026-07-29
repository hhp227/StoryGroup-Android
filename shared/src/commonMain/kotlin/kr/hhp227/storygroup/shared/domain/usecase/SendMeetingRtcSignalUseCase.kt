package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.MeetingRtcSignalType
import kr.hhp227.storygroup.shared.domain.repository.MeetingRepository

/**
 * SDP/ICE 시그널 발신 — 타이핑과 같은 휘발 신호라 실패/미연결이면 조용히 버려지고 예외도 없다.
 * 서버도 수신자가 같은 rtc 방에 없으면 조용히 버린다(PEERS에 나타난 상대에게만 보낼 것).
 */
class SendMeetingRtcSignalUseCase(
    private val meetingRepository: MeetingRepository
) {
    suspend operator fun invoke(meetingId: Long, type: MeetingRtcSignalType, toUserId: Long, payload: String) {
        meetingRepository.sendSignal(meetingId, type, toUserId, payload)
    }
}
