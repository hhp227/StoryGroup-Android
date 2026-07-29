package kr.hhp227.storygroup.ui.screens.meeting

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
import kr.hhp227.storygroup.shared.domain.model.Meeting
import kr.hhp227.storygroup.shared.domain.model.MeetingParticipant
import kr.hhp227.storygroup.shared.domain.model.RtcRoom
import kr.hhp227.storygroup.shared.domain.model.RtcRoomKind
import kr.hhp227.storygroup.shared.domain.usecase.EndMeetingUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetCurrentUserIdUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetIceServersUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetMeetingParticipantsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetMeetingUseCase
import kr.hhp227.storygroup.shared.domain.usecase.JoinMeetingUseCase
import kr.hhp227.storygroup.shared.domain.usecase.LeaveMeetingUseCase
import kr.hhp227.storygroup.shared.domain.usecase.ObserveRtcCallEventsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.ObserveRtcSignalsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.SendRtcSignalUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel
import kr.hhp227.storygroup.ui.rtc.RtcCallController
import kr.hhp227.storygroup.ui.rtc.RtcMediaSessionFactory

/**
 * 회의 상세 — REST 참가 "기록"과 실제 통화(RtcCallController: 로스터/시그널/미디어)를 잇는다.
 * 통화 오케스트레이션(메시 규칙·재연결)은 DM 통화와 공용인 컨트롤러 소관이고, 이 VM은
 * 회의 고유 REST(단건/참가 기록/종료)와 컨트롤러 상태의 UiState 미러링만 담당한다.
 * 종료된 회의는 서버가 rtc 구독을 거부하므로(ERROR 프레임) 종료를 알게 되면 즉시 통화를 접는다.
 * iosApp MeetingDetailViewModel.swift와 1:1 미러
 */
class MeetingDetailViewModel(
    val groupId: Long,
    val meetingId: Long,
    private val getMeetingUseCase: GetMeetingUseCase,
    private val getMeetingParticipantsUseCase: GetMeetingParticipantsUseCase,
    private val joinMeetingUseCase: JoinMeetingUseCase,
    private val leaveMeetingUseCase: LeaveMeetingUseCase,
    private val endMeetingUseCase: EndMeetingUseCase,
    observeRtcCallEventsUseCase: ObserveRtcCallEventsUseCase,
    observeRtcSignalsUseCase: ObserveRtcSignalsUseCase,
    sendRtcSignalUseCase: SendRtcSignalUseCase,
    getIceServersUseCase: GetIceServersUseCase,
    rtcMediaSessionFactory: RtcMediaSessionFactory,
    getCurrentUserIdUseCase: GetCurrentUserIdUseCase
) : ViewModel(), MviViewModel<MeetingDetailViewModel.UiState, MeetingDetailViewModel.Action, MeetingDetailViewModel.Event> {
    private val _uiState = MutableStateFlow(UiState(myUserId = getCurrentUserIdUseCase()))
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<Event>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val event: Flow<Event> = _event.asSharedFlow()

    private val callController = RtcCallController(
        room = RtcRoom(RtcRoomKind.MEETING, meetingId),
        myUserId = _uiState.value.myUserId,
        scope = viewModelScope,
        observeRtcCallEventsUseCase = observeRtcCallEventsUseCase,
        observeRtcSignalsUseCase = observeRtcSignalsUseCase,
        sendRtcSignalUseCase = sendRtcSignalUseCase,
        getIceServersUseCase = getIceServersUseCase,
        mediaSessionFactory = rtcMediaSessionFactory,
        // 재연결이면 REST를 재조회해 끊겨 있던 사이의 기록 공백을 메꾼다(채팅방 미러).
        // 유실 시에도 재조회 — 회의가 그새 종료됐다면 재구독이 계속 거부되므로 종료를 감지해 접는다
        onTopicConnected = { reconnected -> if (reconnected) refreshSilently() },
        onTopicDisconnected = { refreshSilently() }
    )

    override fun onAction(action: Action) {
        when (action) {
            Action.Refresh -> refresh()
            is Action.JoinCall -> joinCall(action.withMedia)
            Action.LeaveCall -> leaveCall()
            Action.EndMeeting -> endMeeting()
            Action.ToggleMic -> callController.toggleMic()
            Action.ToggleCam -> callController.toggleCam()
            Action.DismissActionError -> _uiState.update { it.copy(actionError = null) }
        }
    }

    /** 상세 진입/당겨서 새로고침 — 회의+참가 기록 로드. 종료를 알게 되면 통화도 접는다 */
    private fun refresh() {
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val meeting = getMeetingUseCase(groupId, meetingId)
                val participants = getMeetingParticipantsUseCase(groupId, meetingId)
                meeting to participants
            }.onSuccess { (meeting, participants) ->
                _uiState.update {
                    it.copy(isLoading = false, meeting = meeting, participants = participants)
                }
                if (!meeting.isActive) callController.leave()
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "회의를 불러오지 못했습니다.")
                }
            }
        }
    }

    /** 통화 중 REST 재조회 — 재연결 공백 메꿈용이라 실패해도 조용히 둔다(다음 갱신이 따라잡는다) */
    private fun refreshSilently() {
        viewModelScope.launch {
            runCatching {
                val meeting = getMeetingUseCase(groupId, meetingId)
                val participants = getMeetingParticipantsUseCase(groupId, meetingId)
                meeting to participants
            }.onSuccess { (meeting, participants) ->
                _uiState.update { it.copy(meeting = meeting, participants = participants) }
                if (!meeting.isActive) callController.leave()
            }
        }
    }

    /** 통화 참가 — REST join(기록)과 컨트롤러 통화(구독=입장)를 함께 시작한다(느슨 결합) */
    private fun joinCall(withMedia: Boolean) {
        val meeting = _uiState.value.meeting ?: return
        if (!meeting.isActive || _uiState.value.isJoining || _uiState.value.isInCall) return

        _uiState.update { it.copy(isJoining = true, actionError = null) }
        viewModelScope.launch {
            runCatching { joinMeetingUseCase(groupId, meetingId) }
                .onSuccess {
                    callController.join(withMedia)
                    _uiState.update { it.copy(isJoining = false) }
                }
                .onFailure { e ->
                    // "이미 종료된 회의입니다"(400)가 일상 실패 경로 — 최신 상태로 갱신해 버튼을 접는다
                    _uiState.update { it.copy(isJoining = false, actionError = e.message ?: "통화에 참가하지 못했습니다.") }
                    refreshSilently()
                }
        }
    }

    /** 통화 나가기 — 컨트롤러 퇴장 즉시, REST leave는 기록용이라 실패해도 로컬 퇴장은 유지한다 */
    private fun leaveCall() {
        if (!_uiState.value.isInCall) return

        callController.leave()
        viewModelScope.launch {
            runCatching { leaveMeetingUseCase(groupId, meetingId) }
            refreshSilently()
        }
    }

    /** 회의 종료(호스트 전용) — 성공 시 통화를 접고 종료 상태로 갱신한다 */
    private fun endMeeting() {
        if (_uiState.value.isEnding) return

        _uiState.update { it.copy(isEnding = true, actionError = null) }
        viewModelScope.launch {
            runCatching { endMeetingUseCase(groupId, meetingId) }
                .onSuccess {
                    callController.leave()
                    _uiState.update { it.copy(isEnding = false) }
                    refreshSilently()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isEnding = false, actionError = e.message ?: "회의를 종료하지 못했습니다.") }
                }
        }
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
        // 호스트 판정(종료 버튼 노출)과 글레어 규칙용 — 세션이 있는 한 null이 아니다
        val myUserId: Long? = null,
        // 로드 전 null — 화면은 자리만 비워 두고 상단바를 먼저 그린다
        val meeting: Meeting? = null,
        val participants: List<MeetingParticipant> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null,
        // 참가/종료 실패 문구 — 로드 에러(error)와 달리 상세 화면을 대체하지 않는다
        val actionError: String? = null,
        val isJoining: Boolean = false,
        val isEnding: Boolean = false,
        // 통화 상태 — RtcCallController가 소유하고 여기엔 미러만 담긴다
        val call: RtcCallController.State = RtcCallController.State()
    ) {
        val isInCall: Boolean get() = call.isInCall
        val isHost: Boolean get() = meeting != null && meeting.hostId == myUserId
        // 호스트 이름은 참가 기록에서 파생한다(호스트는 생성 시 자동 참가라 항상 기록에 있다)
        val hostName: String? get() = meeting?.let { m -> participants.firstOrNull { it.userId == m.hostId }?.name }
    }

    sealed interface Action {
        data object Refresh : Action
        /** withMedia=false는 권한 거부 시 로스터 전용 참가(웹 오디오 폴백보다 보수적) */
        data class JoinCall(val withMedia: Boolean) : Action
        data object LeaveCall : Action
        data object EndMeeting : Action
        data object ToggleMic : Action
        data object ToggleCam : Action
        data object DismissActionError : Action
    }

    /** 화면 이동을 유발하는 일회성 이벤트 없음 — 인터페이스 계약용 자리 */
    sealed interface Event
}
