package kr.hhp227.storygroup.shared.bridge

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.shared.domain.model.MeetingCallEvent
import kr.hhp227.storygroup.shared.domain.model.MeetingRtcSignalEvent
import kr.hhp227.storygroup.shared.domain.usecase.ObserveMeetingCallEventsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.ObserveMeetingRtcSignalsUseCase

/**
 * 통화 실시간 이벤트 Flow 대응 핸들 — ChatEventFlowAdapter와 동일 구조.
 * Swift 쪽은 KmpInterop의 KotlinFlowPublisher가 subscribe(onEach:)를 감싼다.
 */
class MeetingCallEventFlowAdapter internal constructor(
    private val source: Flow<MeetingCallEvent>
) {
    fun subscribe(onEach: (MeetingCallEvent) -> Unit): FlowSubscription {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        scope.launch { source.collect { onEach(it) } }
        return FlowSubscription(scope)
    }
}

/** Kotlin의 observeMeetingCallEventsUseCase(meetingId) 호출 대응 — Swift callAsFunction이 감싼다 */
fun ObserveMeetingCallEventsUseCase.eventsFlow(meetingId: Long): MeetingCallEventFlowAdapter =
    MeetingCallEventFlowAdapter(invoke(meetingId))

/** 시그널 채널(/user/queue/rtc) Flow 대응 핸들 — MeetingCallEventFlowAdapter와 동일 구조 */
class MeetingRtcSignalEventFlowAdapter internal constructor(
    private val source: Flow<MeetingRtcSignalEvent>
) {
    fun subscribe(onEach: (MeetingRtcSignalEvent) -> Unit): FlowSubscription {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        scope.launch { source.collect { onEach(it) } }
        return FlowSubscription(scope)
    }
}

/** Kotlin의 observeMeetingRtcSignalsUseCase(meetingId) 호출 대응 — Swift callAsFunction이 감싼다 */
fun ObserveMeetingRtcSignalsUseCase.eventsFlow(meetingId: Long): MeetingRtcSignalEventFlowAdapter =
    MeetingRtcSignalEventFlowAdapter(invoke(meetingId))
