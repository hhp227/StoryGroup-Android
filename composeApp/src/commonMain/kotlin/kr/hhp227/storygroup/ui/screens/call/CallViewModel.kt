package kr.hhp227.storygroup.ui.screens.call

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.BufferOverflow
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
import kr.hhp227.storygroup.shared.domain.model.RtcRoom
import kr.hhp227.storygroup.shared.domain.model.RtcRoomKind
import kr.hhp227.storygroup.shared.domain.usecase.GetCurrentUserIdUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetIceServersUseCase
import kr.hhp227.storygroup.shared.domain.usecase.ObserveRtcCallEventsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.ObserveRtcSignalsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.SendCallInviteUseCase
import kr.hhp227.storygroup.shared.domain.usecase.SendRtcSignalUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel
import kr.hhp227.storygroup.ui.rtc.RtcCallController
import kr.hhp227.storygroup.ui.rtc.RtcMediaSessionFactory

/**
 * 방 통화(DM 1:1·그룹 방 공용, 페이스톡 미러) — 통화 걸기 = rtc 방(chat-rooms/{id}) 입장 +
 * 벨울림(invite) SEND(웹 D6 미러, 그룹 방은 서버가 방 멤버 전원 팬아웃·진행 중 합류엔 안 울림).
 * 오케스트레이션은 RtcCallController 소관이고, 이 VM은 벨울림 발신 시점
 * (시그널 채널 최초 연결 — 세션이 살아있음이 보장된다)과 UiState 미러링만 담당한다.
 * 수락/거절 시그널은 없다 — 건 쪽은 상대가 PEERS에 안 들어오면 대기 표시가 계속될 뿐이고,
 * 받는 쪽 거절은 그냥 배너 닫기다. iosApp CallViewModel.swift와 1:1 미러
 */
class CallViewModel(
    val chatRoomId: Long,
    private val ring: Boolean,
    observeRtcCallEventsUseCase: ObserveRtcCallEventsUseCase,
    observeRtcSignalsUseCase: ObserveRtcSignalsUseCase,
    sendRtcSignalUseCase: SendRtcSignalUseCase,
    getIceServersUseCase: GetIceServersUseCase,
    private val sendCallInviteUseCase: SendCallInviteUseCase,
    rtcMediaSessionFactory: RtcMediaSessionFactory,
    getCurrentUserIdUseCase: GetCurrentUserIdUseCase
) : ViewModel(), MviViewModel<CallViewModel.UiState, CallViewModel.Action, CallViewModel.Event> {
    private val _uiState = MutableStateFlow(UiState(myUserId = getCurrentUserIdUseCase(), isRinging = ring))
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<Event>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val event: Flow<Event> = _event.asSharedFlow()

    private val callController = RtcCallController(
        room = RtcRoom(RtcRoomKind.DIRECT, chatRoomId),
        myUserId = _uiState.value.myUserId,
        scope = viewModelScope,
        observeRtcCallEventsUseCase = observeRtcCallEventsUseCase,
        observeRtcSignalsUseCase = observeRtcSignalsUseCase,
        sendRtcSignalUseCase = sendRtcSignalUseCase,
        getIceServersUseCase = getIceServersUseCase,
        mediaSessionFactory = rtcMediaSessionFactory,
        // 발신이면 벨울림 — 시그널 세션이 살아있는 최초 연결 시점에 1회(재연결 때 다시 울리지 않는다).
        // 그룹 방은 서버가 방 멤버 전원에게 팬아웃하고, 진행 중 통화 합류면 서버 게이트가 걸러준다
        onFirstSignalConnected = { if (ring) sendCallInviteUseCase(chatRoomId) }
    )

    override fun onAction(action: Action) {
        when (action) {
            is Action.Join -> join(action.withMedia)
            Action.HangUp -> hangUp()
            Action.ToggleMic -> callController.toggleMic()
            Action.ToggleCam -> callController.toggleCam()
        }
    }

    /** 화면 진입 시 1회 — 권한 결과(허용=미디어, 거부=로스터 전용)를 안고 바로 입장한다 */
    private fun join(withMedia: Boolean) {
        if (_uiState.value.isJoining || _uiState.value.call.isInCall) return

        _uiState.update { it.copy(isJoining = true) }
        viewModelScope.launch {
            callController.join(withMedia)
            _uiState.update { it.copy(isJoining = false) }
        }
    }

    /** 끊기 — 구독 취소가 곧 퇴장. 별도 종료 시그널은 없다(상대 화면엔 PEERS 축소로 반영) */
    private fun hangUp() {
        callController.leave()
        _event.tryEmit(Event.Ended)
    }

    // 화면 이탈(백스택 pop) — viewModelScope가 구독을 닫아 통화에서 나가지만 미디어는 명시 해제 필요
    override fun onCleared() {
        callController.dispose()
    }

    init {
        // 컨트롤러 통화 상태 → UiState 미러링(화면은 UiState 하나만 본다)
        callController.state
            .onEach { call -> _uiState.update { it.copy(call = call) } }
            .launchIn(viewModelScope)
    }

    data class UiState(
        val myUserId: Long? = null,
        // 발신 여부 — 상대가 아직 안 들어왔을 때 "응답 대기" 표시용
        val isRinging: Boolean = false,
        val isJoining: Boolean = false,
        // 통화 상태 — RtcCallController가 소유하고 여기엔 미러만 담긴다
        val call: RtcCallController.State = RtcCallController.State()
    ) {
        // PEERS는 본인 포함 전체 목록 — 나뿐이면 아직 아무도 통화에 없다
        val isAloneInCall: Boolean get() = call.isInCall && call.peers.size <= 1
    }

    sealed interface Action {
        /** withMedia=false는 권한 거부 시 로스터 전용 참가 */
        data class Join(val withMedia: Boolean) : Action
        data object HangUp : Action
        data object ToggleMic : Action
        data object ToggleCam : Action
    }

    sealed interface Event {
        /** 통화 종료 — 화면이 백스택을 pop한다 */
        data object Ended : Event
    }
}
