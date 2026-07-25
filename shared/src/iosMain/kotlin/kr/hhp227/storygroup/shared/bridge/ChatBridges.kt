package kr.hhp227.storygroup.shared.bridge

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.shared.domain.model.ChatEvent
import kr.hhp227.storygroup.shared.domain.usecase.ObserveChatRoomEventsUseCase

/**
 * 채팅방 실시간 이벤트 Flow 대응 핸들 — 페이징이 아닌 일반 Flow라 어댑터가 단순하다.
 * Swift 쪽은 KmpInterop의 KotlinFlowPublisher가 subscribe(onEach:)를 감싼다.
 */
class ChatEventFlowAdapter internal constructor(
    private val source: Flow<ChatEvent>
) {
    fun subscribe(onEach: (ChatEvent) -> Unit): FlowSubscription {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        scope.launch { source.collect { onEach(it) } }
        return FlowSubscription(scope)
    }
}

/** Kotlin의 observeChatRoomEventsUseCase(chatRoomId) 호출 대응 — Swift callAsFunction이 감싼다 */
fun ObserveChatRoomEventsUseCase.eventsFlow(chatRoomId: Long): ChatEventFlowAdapter =
    ChatEventFlowAdapter(invoke(chatRoomId))
