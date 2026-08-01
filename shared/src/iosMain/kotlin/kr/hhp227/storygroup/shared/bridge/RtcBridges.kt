package kr.hhp227.storygroup.shared.bridge

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.shared.domain.model.RtcCallEvent
import kr.hhp227.storygroup.shared.domain.model.RtcRoom
import kr.hhp227.storygroup.shared.domain.model.RtcRoomKind
import kr.hhp227.storygroup.shared.domain.model.RtcSignalEvent
import kr.hhp227.storygroup.shared.domain.usecase.ObserveRtcCallEventsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.ObserveRtcSignalsUseCase

/**
 * 통화 실시간 이벤트 Flow 대응 핸들 — ChatEventFlowAdapter와 동일 구조.
 * Swift 쪽은 KmpInterop의 KotlinFlowPublisher가 subscribe(onEach:)를 감싼다.
 */
class RtcCallEventFlowAdapter internal constructor(
    private val source: Flow<RtcCallEvent>
) {
    fun subscribe(onEach: (RtcCallEvent) -> Unit): FlowSubscription {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        scope.launch { source.collect { onEach(it) } }
        return FlowSubscription(scope)
    }
}

/** 방 통화(DM·그룹 공용, chat-rooms/{id}) 로스터 구독 대응 — Swift callAsFunction이 감싼다 */
fun ObserveRtcCallEventsUseCase.directEventsFlow(chatRoomId: Long): RtcCallEventFlowAdapter =
    RtcCallEventFlowAdapter(invoke(RtcRoom(RtcRoomKind.DIRECT, chatRoomId)))

/** 시그널 채널(/user/queue/rtc) Flow 대응 핸들 — RtcCallEventFlowAdapter와 동일 구조 */
class RtcSignalEventFlowAdapter internal constructor(
    private val source: Flow<RtcSignalEvent>
) {
    fun subscribe(onEach: (RtcSignalEvent) -> Unit): FlowSubscription {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        scope.launch { source.collect { onEach(it) } }
        return FlowSubscription(scope)
    }
}

/** 방 통화 시그널 채널 구독 대응 */
fun ObserveRtcSignalsUseCase.directEventsFlow(chatRoomId: Long): RtcSignalEventFlowAdapter =
    RtcSignalEventFlowAdapter(invoke(RtcRoom(RtcRoomKind.DIRECT, chatRoomId)))
