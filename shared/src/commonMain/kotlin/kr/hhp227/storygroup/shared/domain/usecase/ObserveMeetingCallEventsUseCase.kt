package kr.hhp227.storygroup.shared.domain.usecase

import kotlinx.coroutines.flow.Flow
import kr.hhp227.storygroup.shared.domain.model.MeetingCallEvent
import kr.hhp227.storygroup.shared.domain.repository.MeetingRepository

/**
 * 통화 실시간 로스터 구독(STOMP) — 서버 계약상 구독 자체가 통화 입장이다.
 * 수집 취소 시 소켓도 함께 닫혀 통화에서 나간다. 종료된 회의는 서버가 구독을 거부하므로
 * 진행 중일 때만 수집해야 한다.
 */
class ObserveMeetingCallEventsUseCase(
    private val meetingRepository: MeetingRepository
) {
    operator fun invoke(meetingId: Long): Flow<MeetingCallEvent> =
        meetingRepository.observeCallEvents(meetingId)
}
