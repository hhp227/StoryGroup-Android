package kr.hhp227.storygroup.ui.rtc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.shared.domain.model.PersonalEvent
import kr.hhp227.storygroup.shared.domain.model.PersonalEventType
import kr.hhp227.storygroup.shared.domain.usecase.ObservePersonalEventsUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel

/**
 * 수신 통화 배너(DM·그룹 방) — 개인 큐(공유 소켓)의 CALL_INVITE를 세션 전역에서 받아 표시한다
 * (웹 app-header 알림 훅의 수락/거절 배너 미러). 벨울림은 DB에 남지 않는 휘발 신호라
 * 일정 시간 뒤 자동으로 사라진다(부재중 이력 없음). 수락 시 화면 이동은 셸(SessionContent) 몫.
 * iosApp IncomingCallViewModel.swift와 1:1 미러
 */
class IncomingCallViewModel(
    observePersonalEventsUseCase: ObservePersonalEventsUseCase
) : ViewModel(), MviViewModel<IncomingCallViewModel.UiState, IncomingCallViewModel.Action, IncomingCallViewModel.Event> {
    private val _uiState = MutableStateFlow(UiState())
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<Event>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val event: Flow<Event> = _event.asSharedFlow()

    // 자동 소거 타이머 — 새 벨울림이 오면 리셋된다
    private var dismissJob: Job? = null

    override fun onAction(action: Action) {
        when (action) {
            Action.Dismiss -> dismiss()
        }
    }

    private fun show(event: PersonalEvent) {
        val chatRoomId = event.chatRoomId ?: return

        dismissJob?.cancel()
        _uiState.update {
            it.copy(
                incomingCall = IncomingCall(
                    chatRoomId = chatRoomId,
                    callerId = event.senderId,
                    callerName = event.senderName ?: "알 수 없음",
                    roomName = event.roomName
                )
            )
        }
        dismissJob = viewModelScope.launch {
            delay(RING_TIMEOUT_MS)
            dismiss()
        }
    }

    private fun dismiss() {
        dismissJob?.cancel()
        dismissJob = null
        _uiState.update { it.copy(incomingCall = null) }
    }

    init {
        // 구독 수명 = 세션 VM 수명 — 로그아웃으로 세션 스토어가 비워지면 함께 정리된다
        observePersonalEventsUseCase()
            .onEach { event -> if (event.type == PersonalEventType.CALL_INVITE) show(event) }
            .launchIn(viewModelScope)
    }

    data class UiState(
        val incomingCall: IncomingCall? = null
    )

    /** 수신 벨울림 — 그 순간에만 의미 있는 휘발 신호(서버 CallInviteEvent 미러) */
    data class IncomingCall(
        val chatRoomId: Long,
        val callerId: Long?,
        val callerName: String,
        // 그룹 방 벨울림이면 방(그룹) 이름 — 배너 제목과 통화 화면 제목에 쓴다. DM이면 null
        val roomName: String? = null
    )

    sealed interface Action {
        data object Dismiss : Action
    }

    /** 화면 이동을 유발하는 일회성 이벤트 없음 — 인터페이스 계약용 자리 */
    sealed interface Event

    private companion object {
        // 웹 벨울림 배너 자동 소거와 결 — 발신 측도 이 즈음이면 대기 표시만 남는다
        const val RING_TIMEOUT_MS = 30_000L
    }
}
