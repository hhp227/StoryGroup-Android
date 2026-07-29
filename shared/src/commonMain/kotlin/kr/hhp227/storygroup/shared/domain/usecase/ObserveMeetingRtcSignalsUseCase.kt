package kr.hhp227.storygroup.shared.domain.usecase

import kotlinx.coroutines.flow.Flow
import kr.hhp227.storygroup.shared.domain.model.MeetingRtcSignalEvent
import kr.hhp227.storygroup.shared.domain.repository.MeetingRepository

/**
 * SDP/ICE 시그널 채널 구독(/user/queue/rtc) — 이 회의 신호만 걸러 흘린다.
 * 시그널 발신도 이 채널의 세션을 빌려 쓰므로 통화 중에는 반드시 수집을 유지해야 한다.
 */
class ObserveMeetingRtcSignalsUseCase(
    private val meetingRepository: MeetingRepository
) {
    operator fun invoke(meetingId: Long): Flow<MeetingRtcSignalEvent> =
        meetingRepository.observeSignalEvents(meetingId)
}
