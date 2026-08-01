package kr.hhp227.storygroup.shared.domain.usecase

import kotlinx.coroutines.flow.Flow
import kr.hhp227.storygroup.shared.domain.model.RtcRoom
import kr.hhp227.storygroup.shared.domain.model.RtcSignalEvent
import kr.hhp227.storygroup.shared.domain.repository.RtcRepository

/**
 * SDP/ICE 시그널 채널 구독(/user/queue/rtc) — 이 방(roomKey) 신호만 걸러 흘린다(회의/DM 공용).
 * 시그널·벨울림 발신도 이 채널의 세션을 빌려 쓰므로 통화 중에는 반드시 수집을 유지해야 한다.
 */
class ObserveRtcSignalsUseCase(
    private val rtcRepository: RtcRepository
) {
    operator fun invoke(room: RtcRoom): Flow<RtcSignalEvent> =
        rtcRepository.observeSignalEvents(room)
}
