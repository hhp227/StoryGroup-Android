package kr.hhp227.storygroup.ui.rtc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.shared.domain.model.IceServer
import kr.hhp227.storygroup.shared.domain.model.RtcCallEvent
import kr.hhp227.storygroup.shared.domain.model.RtcCallEventType
import kr.hhp227.storygroup.shared.domain.model.RtcCallPeer
import kr.hhp227.storygroup.shared.domain.model.RtcRoom
import kr.hhp227.storygroup.shared.domain.model.RtcSignalEvent
import kr.hhp227.storygroup.shared.domain.model.RtcSignalEventType
import kr.hhp227.storygroup.shared.domain.model.RtcSignalType
import kr.hhp227.storygroup.shared.domain.usecase.GetIceServersUseCase
import kr.hhp227.storygroup.shared.domain.usecase.ObserveRtcCallEventsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.ObserveRtcSignalsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.SendRtcSignalUseCase

/**
 * 통화 오케스트레이션 공용 컨트롤러 — 그룹 회의 VM과 DM 통화 VM이 함께 쓴다(웹 useRtcSession 미러).
 * 로스터 토픽+시그널 채널 구독, ICE 구성 조회, 플랫폼 미디어 세션, 풀 메시 구성을 한곳에 소유하고
 * VM은 [state]를 UiState로 미러링만 한다.
 *
 * 메시 규칙(전부 웹과 동일):
 * - 글레어 방지는 "userId 작은 쪽이 offer"(서버 강제 아님, 클라 규칙 D5)
 * - PEERS는 전체 목록 — 스냅숏과 아는 피어의 차집합으로 연결을 만들고/정리한다(자가 복구)
 * - 시그널 채널이 CONNECTED여야 offer를 시작한다(그 전 SEND는 조용히 유실)
 * - 어느 채널이든 끊기면 메시 전체 해체 후 재연결 → PEERS 재수신으로 재구축(D7)
 */
class RtcCallController(
    private val room: RtcRoom,
    private val myUserId: Long?,
    private val scope: CoroutineScope,
    private val observeRtcCallEventsUseCase: ObserveRtcCallEventsUseCase,
    private val observeRtcSignalsUseCase: ObserveRtcSignalsUseCase,
    private val sendRtcSignalUseCase: SendRtcSignalUseCase,
    private val getIceServersUseCase: GetIceServersUseCase,
    private val mediaSessionFactory: RtcMediaSessionFactory,
    /** 로스터 토픽 연결/재연결 — 회의 VM이 REST 재조회(공백 메꿈)를 거는 지점 */
    private val onTopicConnected: (reconnected: Boolean) -> Unit = {},
    private val onTopicDisconnected: () -> Unit = {},
    /** 시그널 채널 최초 연결 — DM 발신 측이 벨울림(invite)을 보내는 지점(세션이 살아있음이 보장된다) */
    private val onFirstSignalConnected: suspend () -> Unit = {}
) {

    data class State(
        val isInCall: Boolean = false,
        // isInCall 중 로스터 토픽 소켓이 살아있는지 — 끊기면 "재연결 중" 표시용
        val isConnected: Boolean = false,
        val peers: List<RtcCallPeer> = emptyList(),
        // 미디어 미지원 플랫폼/권한 거부면 false — 로스터 전용 통화
        val isMediaActive: Boolean = false,
        val micOn: Boolean = true,
        val camOn: Boolean = true,
        // 스피커폰 출력 — 영상통화라 기본 ON(웹엔 없는 모바일 전용, 라우팅은 플랫폼 미디어 세션 소관)
        val speakerOn: Boolean = true,
        // 화면 공유 중 — 공유 중엔 localVideo가 화면 트랙이고 카메라 토글은 잠긴다(웹 D9)
        val sharing: Boolean = false,
        val localVideo: RtcVideoTrackHandle? = null,
        val remoteVideos: Map<Long, RtcVideoTrackHandle> = emptyMap()
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var callJob: Job? = null
    private var signalJob: Job? = null
    private var mediaJob: Job? = null
    private var mediaSession: RtcMediaSession? = null
    // 첫 CONNECTED는 진입 로드와 경합하므로 콜백에 reconnected=false로 알린다(채팅방 미러)
    private var hasTopicConnectedOnce = false
    private var hasSignalConnectedOnce = false
    private var signalConnected = false
    // 메시 상태 — latest는 서버 로스터 스냅숏(나 제외), known은 지금 피어 연결이 있는 상대
    private var latestPeerIds = setOf<Long>()
    private var knownPeerIds = setOf<Long>()

    /** 통화 참가 — ICE 구성 조회(실패 시 STUN 폴백) → 미디어 시작 → 로스터+시그널 구독 */
    suspend fun join(withMedia: Boolean) {
        if (_state.value.isInCall) return

        if (withMedia) startMediaSession()
        startSubscriptions()
        _state.update { it.copy(isInCall = true, isMediaActive = mediaSession != null) }
    }

    /** 통화 나가기 — 구독 취소가 곧 퇴장(소켓 닫힘). 미디어도 함께 해제한다 */
    fun leave() {
        callJob?.cancel()
        callJob = null
        signalJob?.cancel()
        signalJob = null
        mediaJob?.cancel()
        mediaJob = null
        // 트랙 핸들을 먼저 상태에서 걷어 화면 타일이 떨어진 뒤 네이티브 자원을 해제한다
        _state.update {
            it.copy(
                isInCall = false,
                isConnected = false,
                isMediaActive = false,
                peers = emptyList(),
                sharing = false,
                localVideo = null,
                remoteVideos = emptyMap()
            )
        }
        mediaSession?.dispose()
        mediaSession = null
        signalConnected = false
        latestPeerIds = emptySet()
        knownPeerIds = emptySet()
    }

    fun toggleMic() {
        val micOn = !_state.value.micOn

        _state.update { it.copy(micOn = micOn) }
        mediaSession?.setMicEnabled(micOn)
    }

    fun toggleCam() {
        // 공유 중엔 잠금(D9) — 전송 중인 비디오가 카메라가 아니라 화면이다(웹 toggleCam 미러)
        if (_state.value.sharing) return
        val camOn = !_state.value.camOn

        _state.update { it.copy(camOn = camOn) }
        mediaSession?.setCamEnabled(camOn)
    }

    fun toggleSpeaker() {
        val speakerOn = !_state.value.speakerOn

        _state.update { it.copy(speakerOn = speakerOn) }
        mediaSession?.setSpeakerEnabled(speakerOn)
    }

    /** 화면 공유 시작 — 동의 토큰은 플랫폼 런처(rememberRtcScreenCaptureRequester)가 만든다 */
    fun startScreenShare(grant: RtcScreenCaptureGrant) {
        mediaSession?.startScreenShare(grant)
    }

    fun stopScreenShare() {
        mediaSession?.stopScreenShare()
    }

    /** VM onCleared에서 호출 — 코루틴은 스코프가 정리하지만 네이티브 미디어는 명시 해제가 필요하다 */
    fun dispose() {
        mediaSession?.dispose()
        mediaSession = null
    }

    private suspend fun startMediaSession() {
        // 구성 조회 실패가 통화를 막으면 안 된다 — 웹과 같은 STUN 폴백
        val iceServers = runCatching { getIceServersUseCase() }.getOrNull() ?: FALLBACK_ICE_SERVERS
        val session = mediaSessionFactory.create(iceServers) ?: return

        mediaSession = session
        session.setMicEnabled(_state.value.micOn)
        session.setCamEnabled(_state.value.camOn)
        session.setSpeakerEnabled(_state.value.speakerOn)
        session.start()
        mediaJob = scope.launch {
            launch {
                session.localVideo.collect { video -> _state.update { it.copy(localVideo = video) } }
            }
            launch {
                session.remoteVideos.collect { videos -> _state.update { it.copy(remoteVideos = videos) } }
            }
            launch {
                session.screenSharing.collect { sharing -> _state.update { it.copy(sharing = sharing) } }
            }
            launch {
                session.outgoingSignals.collect { signal ->
                    sendRtcSignalUseCase(room, signal.type, signal.toUserId, signal.payload)
                }
            }
        }
    }

    private fun startSubscriptions() {
        hasTopicConnectedOnce = false
        hasSignalConnectedOnce = false
        signalConnected = false
        latestPeerIds = emptySet()
        knownPeerIds = emptySet()
        callJob?.cancel()
        callJob = observeRtcCallEventsUseCase(room)
            .onEach(::handleCallEvent)
            .launchIn(scope)
        signalJob?.cancel()
        signalJob = observeRtcSignalsUseCase(room)
            .onEach(::handleSignalEvent)
            .launchIn(scope)
    }

    private fun handleCallEvent(event: RtcCallEvent) {
        when (event.type) {
            RtcCallEventType.CONNECTED -> {
                _state.update { it.copy(isConnected = true) }
                onTopicConnected(hasTopicConnectedOnce)
                hasTopicConnectedOnce = true
            }
            // 어느 채널이든 유실이면 메시 전체 해체 — 재연결 후 PEERS 전체 목록으로 재구축(D7)
            RtcCallEventType.DISCONNECTED -> {
                _state.update { it.copy(isConnected = false) }
                tearDownMesh()
                onTopicDisconnected()
            }
            RtcCallEventType.PEERS -> {
                _state.update { it.copy(peers = event.peers) }
                latestPeerIds = event.peers.map { it.userId }.filter { it != myUserId }.toSet()
                reconcileMesh()
            }
        }
    }

    private fun handleSignalEvent(event: RtcSignalEvent) {
        when (event.type) {
            RtcSignalEventType.CONNECTED -> {
                signalConnected = true
                if (!hasSignalConnectedOnce) {
                    hasSignalConnectedOnce = true
                    scope.launch { onFirstSignalConnected() }
                }
                reconcileMesh()
            }
            RtcSignalEventType.DISCONNECTED -> {
                signalConnected = false
                tearDownMesh()
            }
            RtcSignalEventType.SIGNAL -> {
                val session = mediaSession ?: return
                val from = event.fromUserId ?: return
                val signalType = event.signalType ?: return
                val payload = event.payload ?: return

                // 로스터보다 신호가 먼저 닿을 수 있다 — OFFER를 받으면 수신측 피어를 즉석 생성
                if (signalType == RtcSignalType.OFFER && from !in knownPeerIds) {
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
        val myUserId = myUserId ?: return

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

    private companion object {
        // 웹 useRtcSession의 STUN 폴백 미러 — ICE 구성 조회 실패가 통화를 막으면 안 된다
        val FALLBACK_ICE_SERVERS = listOf(IceServer(urls = listOf("stun:stun.l.google.com:19302")))
    }
}
