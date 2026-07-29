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
import kr.hhp227.storygroup.shared.domain.model.IceServer
import kr.hhp227.storygroup.shared.domain.model.Meeting
import kr.hhp227.storygroup.shared.domain.model.MeetingCallEvent
import kr.hhp227.storygroup.shared.domain.model.MeetingCallEventType
import kr.hhp227.storygroup.shared.domain.model.MeetingCallPeer
import kr.hhp227.storygroup.shared.domain.model.MeetingParticipant
import kr.hhp227.storygroup.shared.domain.model.MeetingRtcSignalEvent
import kr.hhp227.storygroup.shared.domain.model.MeetingRtcSignalEventType
import kr.hhp227.storygroup.shared.domain.model.MeetingRtcSignalType
import kr.hhp227.storygroup.shared.domain.usecase.EndMeetingUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetCurrentUserIdUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetIceServersUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetMeetingParticipantsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetMeetingUseCase
import kr.hhp227.storygroup.shared.domain.usecase.JoinMeetingUseCase
import kr.hhp227.storygroup.shared.domain.usecase.LeaveMeetingUseCase
import kr.hhp227.storygroup.shared.domain.usecase.ObserveMeetingCallEventsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.ObserveMeetingRtcSignalsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.SendMeetingRtcSignalUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel
import kr.hhp227.storygroup.ui.rtc.RtcMediaSession
import kr.hhp227.storygroup.ui.rtc.RtcMediaSessionFactory
import kr.hhp227.storygroup.ui.rtc.RtcVideoTrackHandle

/**
 * 회의 상세 — REST 참가 "기록"과 rtc 토픽의 실시간 통화 로스터에 더해 실제 미디어(카메라/마이크)
 * 통화를 붙인다(웹 use-rtc-session 미러). 통화 입장은 서버 계약상 rtc 토픽 구독 자체다.
 *
 * 미디어 메시 규칙(전부 웹과 동일):
 * - 풀 메시 P2P, 글레어 방지는 "userId 작은 쪽이 offer"(서버 강제 아님, 클라 규칙 D5)
 * - PEERS는 전체 목록 — 스냅숏과 아는 피어의 차집합으로 연결을 만들고/정리한다(자가 복구)
 * - 시그널 채널(/user/queue/rtc)이 CONNECTED여야 offer를 시작한다(그 전 SEND는 조용히 유실)
 * - 어느 채널이든 끊기면 메시 전체 해체 후 재연결 → PEERS 재수신으로 재구축(D7)
 * 미디어 미지원 플랫폼(Desktop)은 팩토리가 null을 줘 로스터 전용으로 동작한다.
 * 종료된 회의는 서버가 rtc 구독을 거부하므로(ERROR 프레임) 진행 중일 때만 구독하고,
 * 종료를 알게 되면 즉시 접는다. iosApp MeetingDetailViewModel.swift와 1:1 미러
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
    private val observeMeetingRtcSignalsUseCase: ObserveMeetingRtcSignalsUseCase,
    private val sendMeetingRtcSignalUseCase: SendMeetingRtcSignalUseCase,
    private val getIceServersUseCase: GetIceServersUseCase,
    private val rtcMediaSessionFactory: RtcMediaSessionFactory,
    getCurrentUserIdUseCase: GetCurrentUserIdUseCase
) : ViewModel(), MviViewModel<MeetingDetailViewModel.UiState, MeetingDetailViewModel.Action, MeetingDetailViewModel.Event> {
    private val _uiState = MutableStateFlow(UiState(myUserId = getCurrentUserIdUseCase()))
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<Event>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val event: Flow<Event> = _event.asSharedFlow()

    // 통화 구독 잡들 — null이 아니면 통화 참여 중. 취소가 곧 통화 퇴장(소켓 닫힘)이다
    private var callJob: Job? = null
    private var signalJob: Job? = null
    private var mediaJob: Job? = null
    private var mediaSession: RtcMediaSession? = null
    // 첫 CONNECTED는 진입 로드와 경합하므로 재조회를 건너뛴다(채팅방 hasConnectedOnce 미러)
    private var hasConnectedOnce = false
    // 메시 상태 — latest는 서버 로스터 스냅숏(나 제외), known은 지금 피어 연결이 있는 상대
    private var latestPeerIds = setOf<Long>()
    private var knownPeerIds = setOf<Long>()
    private var signalConnected = false

    override fun onAction(action: Action) {
        when (action) {
            Action.Refresh -> refresh()
            is Action.JoinCall -> joinCall(action.withMedia)
            Action.LeaveCall -> leaveCall()
            Action.EndMeeting -> endMeeting()
            Action.ToggleMic -> toggleMic()
            Action.ToggleCam -> toggleCam()
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

    /**
     * 통화 참가 — REST join(기록) → ICE 구성 조회(실패 시 STUN 폴백) → 미디어 세션 시작 →
     * 로스터 토픽+시그널 채널 구독. withMedia=false(권한 거부)면 로스터 전용으로 참가한다.
     */
    private fun joinCall(withMedia: Boolean) {
        val meeting = _uiState.value.meeting ?: return
        if (!meeting.isActive || _uiState.value.isJoining || _uiState.value.isInCall) return

        _uiState.update { it.copy(isJoining = true, actionError = null) }
        viewModelScope.launch {
            runCatching { joinMeetingUseCase(groupId, meetingId) }
                .onSuccess {
                    if (withMedia) startMediaSession()
                    startCallSubscriptions()
                    _uiState.update {
                        it.copy(isJoining = false, isInCall = true, isMediaActive = mediaSession != null)
                    }
                }
                .onFailure { e ->
                    // "이미 종료된 회의입니다"(400)가 일상 실패 경로 — 최신 상태로 갱신해 버튼을 접는다
                    _uiState.update { it.copy(isJoining = false, actionError = e.message ?: "통화에 참가하지 못했습니다.") }
                    refreshSilently()
                }
        }
    }

    private suspend fun startMediaSession() {
        // 구성 조회 실패가 통화를 막으면 안 된다 — 웹과 같은 STUN 폴백
        val iceServers = runCatching { getIceServersUseCase() }.getOrNull() ?: FALLBACK_ICE_SERVERS
        val session = rtcMediaSessionFactory.create(iceServers) ?: return

        mediaSession = session
        session.setMicEnabled(_uiState.value.micOn)
        session.setCamEnabled(_uiState.value.camOn)
        session.start()
        mediaJob = viewModelScope.launch {
            launch {
                session.localVideo.collect { video -> _uiState.update { it.copy(localVideo = video) } }
            }
            launch {
                session.remoteVideos.collect { videos -> _uiState.update { it.copy(remoteVideos = videos) } }
            }
            launch {
                session.outgoingSignals.collect { signal ->
                    sendMeetingRtcSignalUseCase(meetingId, signal.type, signal.toUserId, signal.payload)
                }
            }
        }
    }

    private fun startCallSubscriptions() {
        hasConnectedOnce = false
        signalConnected = false
        latestPeerIds = emptySet()
        knownPeerIds = emptySet()
        callJob?.cancel()
        callJob = observeMeetingCallEventsUseCase(meetingId)
            .onEach(::handleCallEvent)
            .launchIn(viewModelScope)
        signalJob?.cancel()
        signalJob = observeMeetingRtcSignalsUseCase(meetingId)
            .onEach(::handleSignalEvent)
            .launchIn(viewModelScope)
    }

    private fun handleCallEvent(event: MeetingCallEvent) {
        when (event.type) {
            // 재연결이면 REST를 재조회해 끊겨 있던 사이의 기록 공백을 메꾼다(채팅방 미러)
            MeetingCallEventType.CONNECTED -> {
                _uiState.update { it.copy(isCallConnected = true) }
                if (hasConnectedOnce) refreshSilently() else hasConnectedOnce = true
            }
            // 어느 채널이든 유실이면 메시 전체 해체 — 재연결 후 PEERS 전체 목록으로 재구축(D7).
            // 회의가 그새 종료됐다면 재구독이 계속 거부되므로 재조회로 종료를 감지해 접는다
            MeetingCallEventType.DISCONNECTED -> {
                _uiState.update { it.copy(isCallConnected = false) }
                tearDownMesh()
                refreshSilently()
            }
            MeetingCallEventType.PEERS -> {
                _uiState.update { it.copy(callPeers = event.peers) }
                latestPeerIds = event.peers.map { it.userId }.filter { it != _uiState.value.myUserId }.toSet()
                reconcileMesh()
            }
        }
    }

    private fun handleSignalEvent(event: MeetingRtcSignalEvent) {
        when (event.type) {
            MeetingRtcSignalEventType.CONNECTED -> {
                signalConnected = true
                reconcileMesh()
            }
            MeetingRtcSignalEventType.DISCONNECTED -> {
                signalConnected = false
                tearDownMesh()
            }
            MeetingRtcSignalEventType.SIGNAL -> {
                val session = mediaSession ?: return
                val from = event.fromUserId ?: return
                val signalType = event.signalType ?: return
                val payload = event.payload ?: return

                // 로스터보다 신호가 먼저 닿을 수 있다 — OFFER를 받으면 수신측 피어를 즉석 생성
                if (signalType == MeetingRtcSignalType.OFFER && from !in knownPeerIds) {
                    session.createPeer(from, initiator = false)
                    knownPeerIds = knownPeerIds + from
                }
                session.applySignal(from, signalType, payload)
            }
        }
    }

    /**
     * PEERS 스냅숏과 아는 피어의 차집합으로 메시를 맞춘다 — 시그널 채널이 살아 있어야
     * offer가 유실되지 않으므로 signalConnected 전에는 미룬다(연결되는 순간 재호출).
     */
    private fun reconcileMesh() {
        val session = mediaSession ?: return
        if (!signalConnected) return
        val myUserId = _uiState.value.myUserId ?: return

        (latestPeerIds - knownPeerIds).forEach { peerId ->
            // 글레어 방지 — 양쪽이 같은 규칙을 보므로 동시 offer가 없다(웹 D5 미러)
            session.createPeer(peerId, initiator = myUserId < peerId)
        }
        (knownPeerIds - latestPeerIds).forEach(session::closePeer)
        knownPeerIds = latestPeerIds
    }

    /** 메시 해체(로컬 미디어 유지) — 시그널링을 잃었을 때. 재연결 → PEERS 재수신이 재구축한다 */
    private fun tearDownMesh() {
        mediaSession?.closeAllPeers()
        knownPeerIds = emptySet()
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

    private fun toggleMic() {
        val micOn = !_uiState.value.micOn

        _uiState.update { it.copy(micOn = micOn) }
        mediaSession?.setMicEnabled(micOn)
    }

    private fun toggleCam() {
        val camOn = !_uiState.value.camOn

        _uiState.update { it.copy(camOn = camOn) }
        mediaSession?.setCamEnabled(camOn)
    }

    private fun stopCall() {
        callJob?.cancel()
        callJob = null
        signalJob?.cancel()
        signalJob = null
        mediaJob?.cancel()
        mediaJob = null
        // 트랙 핸들을 먼저 상태에서 걷어 화면 타일이 떨어진 뒤 네이티브 자원을 해제한다
        if (_uiState.value.isInCall || _uiState.value.callPeers.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    isInCall = false,
                    isCallConnected = false,
                    isMediaActive = false,
                    callPeers = emptyList(),
                    localVideo = null,
                    remoteVideos = emptyMap()
                )
            }
        }
        mediaSession?.dispose()
        mediaSession = null
        signalConnected = false
        latestPeerIds = emptySet()
        knownPeerIds = emptySet()
    }

    // 화면 이탈(백스택 pop) — viewModelScope가 구독을 닫아 통화에서 나가지만 미디어는 명시 해제 필요
    override fun onCleared() {
        mediaSession?.dispose()
        mediaSession = null
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
        // 통화 참여 여부(구독 유지 여부) — isCallConnected는 그중 소켓이 살아있는지
        val isInCall: Boolean = false,
        val isCallConnected: Boolean = false,
        val callPeers: List<MeetingCallPeer> = emptyList(),
        // 미디어 — 미지원 플랫폼/권한 거부면 isMediaActive=false로 로스터 전용 통화
        val isMediaActive: Boolean = false,
        val micOn: Boolean = true,
        val camOn: Boolean = true,
        val localVideo: RtcVideoTrackHandle? = null,
        val remoteVideos: Map<Long, RtcVideoTrackHandle> = emptyMap()
    ) {
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

    private companion object {
        // 웹 useRtcSession의 STUN 폴백 미러 — ICE 구성 조회 실패가 통화를 막으면 안 된다
        val FALLBACK_ICE_SERVERS = listOf(IceServer(urls = listOf("stun:stun.l.google.com:19302")))
    }
}
