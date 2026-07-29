package kr.hhp227.storygroup.ui.screens.meeting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
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
import kr.hhp227.storygroup.shared.domain.model.MeetingCallEvent
import kr.hhp227.storygroup.shared.domain.model.MeetingCallEventType
import kr.hhp227.storygroup.shared.domain.model.MeetingCallPeer
import kr.hhp227.storygroup.shared.domain.model.MeetingParticipant
import kr.hhp227.storygroup.shared.domain.usecase.EndMeetingUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetCurrentUserIdUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetMeetingParticipantsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetMeetingUseCase
import kr.hhp227.storygroup.shared.domain.usecase.JoinMeetingUseCase
import kr.hhp227.storygroup.shared.domain.usecase.LeaveMeetingUseCase
import kr.hhp227.storygroup.shared.domain.usecase.ObserveMeetingCallEventsUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel

/**
 * 회의 상세 — REST 참가 "기록"과 rtc 토픽의 실시간 통화 로스터를 함께 보여준다(웹 meeting-detail 미러).
 * 통화 입장은 서버 계약상 rtc 토픽 구독 자체다: 참가 = REST join(기록) + 구독 시작, 나가기 = 구독
 * 취소 + REST leave — 두 축은 느슨 결합이라 REST 실패가 통화를 막지 않는다. 미디어(카메라/마이크)는
 * 후속 마일스톤이라 지금은 로스터 표시까지만 한다. 종료된 회의는 서버가 rtc 구독을 거부하므로
 * (ERROR 프레임 → 무한 재연결) 진행 중일 때만 구독하고, 종료를 알게 되면 즉시 접는다.
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
    private val observeMeetingCallEventsUseCase: ObserveMeetingCallEventsUseCase,
    getCurrentUserIdUseCase: GetCurrentUserIdUseCase
) : ViewModel(), MviViewModel<MeetingDetailViewModel.UiState, MeetingDetailViewModel.Action, MeetingDetailViewModel.Event> {
    private val _uiState = MutableStateFlow(UiState(myUserId = getCurrentUserIdUseCase()))
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<Event>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val event: Flow<Event> = _event.asSharedFlow()

    // 통화 구독 잡 — null이 아니면 통화 참여 중. 취소가 곧 통화 퇴장(소켓 닫힘)이다
    private var callJob: Job? = null
    // 첫 CONNECTED는 진입 로드와 경합하므로 재조회를 건너뛴다(채팅방 hasConnectedOnce 미러)
    private var hasConnectedOnce = false

    override fun onAction(action: Action) {
        when (action) {
            Action.Refresh -> refresh()
            Action.JoinCall -> joinCall()
            Action.LeaveCall -> leaveCall()
            Action.EndMeeting -> endMeeting()
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
                if (!meeting.isActive) stopCall()
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
                if (!meeting.isActive) stopCall()
            }
        }
    }

    /** 통화 참가 — REST join(기록)과 rtc 구독(입장)을 함께 시작한다 */
    private fun joinCall() {
        val meeting = _uiState.value.meeting ?: return
        if (!meeting.isActive || _uiState.value.isJoining || _uiState.value.isInCall) return

        _uiState.update { it.copy(isJoining = true, actionError = null) }
        viewModelScope.launch {
            runCatching { joinMeetingUseCase(groupId, meetingId) }
                .onSuccess {
                    startCallSubscription()
                    _uiState.update { it.copy(isJoining = false, isInCall = true) }
                }
                .onFailure { e ->
                    // "이미 종료된 회의입니다"(400)가 일상 실패 경로 — 최신 상태로 갱신해 버튼을 접는다
                    _uiState.update { it.copy(isJoining = false, actionError = e.message ?: "통화에 참가하지 못했습니다.") }
                    refreshSilently()
                }
        }
    }

    private fun startCallSubscription() {
        hasConnectedOnce = false
        callJob?.cancel()
        callJob = observeMeetingCallEventsUseCase(meetingId)
            .onEach(::handleCallEvent)
            .launchIn(viewModelScope)
    }

    private fun handleCallEvent(event: MeetingCallEvent) {
        when (event.type) {
            // 재연결이면 REST를 재조회해 끊겨 있던 사이의 기록 공백을 메꾼다(채팅방 미러)
            MeetingCallEventType.CONNECTED -> {
                _uiState.update { it.copy(isCallConnected = true) }
                if (hasConnectedOnce) refreshSilently() else hasConnectedOnce = true
            }
            // 로스터는 비우지 않는다 — PEERS가 전체 목록이라 재연결 후 자가 복구된다.
            // 회의가 그새 종료됐다면 재구독이 계속 거부되므로 재조회로 종료를 감지해 접는다
            MeetingCallEventType.DISCONNECTED -> {
                _uiState.update { it.copy(isCallConnected = false) }
                refreshSilently()
            }
            MeetingCallEventType.PEERS -> _uiState.update { it.copy(callPeers = event.peers) }
        }
    }

    /** 통화 나가기 — 구독 취소가 곧 퇴장. REST leave는 기록용이라 실패해도 로컬 퇴장은 유지한다 */
    private fun leaveCall() {
        if (!_uiState.value.isInCall) return

        stopCall()
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
                    stopCall()
                    _uiState.update { it.copy(isEnding = false) }
                    refreshSilently()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isEnding = false, actionError = e.message ?: "회의를 종료하지 못했습니다.") }
                }
        }
    }

    private fun stopCall() {
        callJob?.cancel()
        callJob = null
        if (_uiState.value.isInCall || _uiState.value.callPeers.isNotEmpty()) {
            _uiState.update { it.copy(isInCall = false, isCallConnected = false, callPeers = emptyList()) }
        }
    }

    data class UiState(
        // 호스트 판정(종료 버튼 노출)용 — 세션이 있는 한 null이 아니다
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
        // 통화 참여 여부(구독 유지 여부) — isCallConnected는 그중 소켓이 살아있는지
        val isInCall: Boolean = false,
        val isCallConnected: Boolean = false,
        val callPeers: List<MeetingCallPeer> = emptyList()
    ) {
        val isHost: Boolean get() = meeting != null && meeting.hostId == myUserId
        // 호스트 이름은 참가 기록에서 파생한다(호스트는 생성 시 자동 참가라 항상 기록에 있다)
        val hostName: String? get() = meeting?.let { m -> participants.firstOrNull { it.userId == m.hostId }?.name }
    }

    sealed interface Action {
        data object Refresh : Action
        data object JoinCall : Action
        data object LeaveCall : Action
        data object EndMeeting : Action
        data object DismissActionError : Action
    }

    /** 화면 이동을 유발하는 일회성 이벤트 없음 — 인터페이스 계약용 자리 */
    sealed interface Event
}
