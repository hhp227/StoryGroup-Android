import Combine
import Foundation
import Shared
import WebRTC

/// 방 통화(DM 1:1·그룹 방 공용, 페이스톡 미러) — composeApp CallViewModel.kt+RtcCallController.kt의
/// iOS 통합판. 통화 걸기 = rtc 방(chat-rooms/{id}) 로스터 토픽 구독(=입장) + 벨울림(invite) SEND
/// (웹 D6 미러, 그룹 방은 서버가 방 멤버 전원 팬아웃·진행 중 합류엔 안 울림).
///
/// 메시 규칙(전부 웹·Compose와 동일):
/// - 글레어 방지는 "userId 작은 쪽이 offer"(서버 강제 아님, 클라 규칙 D5)
/// - PEERS는 전체 목록 — 스냅숏과 아는 피어의 차집합으로 연결을 만들고/정리한다(자가 복구)
/// - 시그널 채널이 CONNECTED여야 offer를 시작한다(그 전 SEND는 조용히 유실)
/// - 어느 채널이든 끊기면 메시 전체 해체 후 재연결 → PEERS 재수신으로 재구축(D7)
///
/// 미디어는 RtcMediaSession(stasel/WebRTC) — 권한 거부 시 로스터 전용 참가(Compose 미러).
final class CallViewModel: MviViewModel {
    @Published private(set) var uiState: UiState

    let event = PassthroughSubject<Event, Never>()

    let chatRoomId: Int64

    private let ring: Bool

    private let observeRtcCallEventsUseCase: ObserveRtcCallEventsUseCase

    private let observeRtcSignalsUseCase: ObserveRtcSignalsUseCase

    private let sendRtcSignalUseCase: SendRtcSignalUseCase

    private let sendCallInviteUseCase: SendCallInviteUseCase

    private let getIceServersUseCase: GetIceServersUseCase

    private var mediaSession: RtcMediaSession?

    /// 통화(로스터 토픽) 구독 — 해지(cancel)가 곧 통화 퇴장(Kotlin RtcCallController.leave 대응)
    private var callCancellable: AnyCancellable?

    /// 시그널 채널 구독 — 벨울림·SDP/ICE SEND가 이 소켓의 살아있는 세션을 빌려 쓴다
    private var signalCancellable: AnyCancellable?

    /// 발신 벨울림은 시그널 채널 최초 연결에 1회(Kotlin hasSignalConnectedOnce 미러)
    private var hasSignalConnectedOnce = false

    private var signalConnected = false

    /// 메시 상태 — latest는 서버 로스터 스냅숏(나 제외), known은 지금 피어 연결이 있는 상대
    private var latestPeerIds = Set<Int64>()

    private var knownPeerIds = Set<Int64>()

    func onAction(_ action: Action) {
        switch action {
        case .join(let withMedia): join(withMedia: withMedia)
        case .hangUp: hangUp()
        case .toggleMic: toggleMic()
        case .toggleCam: toggleCam()
        }
    }

    /// 화면 진입 시 1회 — 권한 결과(허용=미디어, 거부=로스터 전용)를 안고 바로 입장한다
    private func join(withMedia: Bool) {
        if uiState.isJoining || uiState.call.isInCall { return }

        uiState.isJoining = true
        Task { @MainActor in
            if withMedia { await startMediaSession() }
            startSubscriptions()
            uiState.call.isInCall = true
            uiState.call.isMediaActive = mediaSession != nil
            uiState.isJoining = false
        }
    }

    /// 끊기 — 구독 취소가 곧 퇴장. 별도 종료 시그널은 없다(상대 화면엔 PEERS 축소로 반영)
    private func hangUp() {
        leave()
        event.send(.ended)
    }

    private func leave() {
        callCancellable?.cancel()
        callCancellable = nil
        signalCancellable?.cancel()
        signalCancellable = nil
        // 트랙을 먼저 상태에서 걷어 화면 타일이 떨어진 뒤 네이티브 자원을 해제한다(Compose 미러)
        uiState.call = CallState()
        mediaSession?.dispose()
        mediaSession = nil
        signalConnected = false
        latestPeerIds = []
        knownPeerIds = []
    }

    private func toggleMic() {
        uiState.call.micOn.toggle()
        mediaSession?.setMicEnabled(uiState.call.micOn)
    }

    private func toggleCam() {
        uiState.call.camOn.toggle()
        mediaSession?.setCamEnabled(uiState.call.camOn)
    }

    /// ICE 구성 조회(실패 시 STUN 폴백) → 미디어 세션 시작(Compose startMediaSession 미러)
    @MainActor private func startMediaSession() async {
        // 구성 조회 실패가 통화를 막으면 안 된다 — 웹·Compose와 같은 STUN 폴백
        let iceServers = (try? await getIceServersUseCase.invoke())
            ?? [Shared.IceServer(urls: ["stun:stun.l.google.com:19302"], username: nil, credential: nil)]
        let session = RtcMediaSession(iceServers: iceServers)

        session.onLocalVideoTrack = { [weak self] track in self?.uiState.call.localVideoTrack = track }
        session.onRemoteVideoTrack = { [weak self] peerId, track in
            if let track {
                self?.uiState.call.remoteVideoTracks[peerId] = track
            } else {
                self?.uiState.call.remoteVideoTracks.removeValue(forKey: peerId)
            }
        }
        session.onOutgoingSignal = { [weak self] signal in self?.relaySignal(signal) }
        session.setMicEnabled(uiState.call.micOn)
        session.setCamEnabled(uiState.call.camOn)
        mediaSession = session
        session.start()
    }

    private func startSubscriptions() {
        // 구독 클로저는 VM 수명 밖에서도 보관된다 — self 대신 지역 캡처로 순환 참조를 끊는다
        let observeRtcCallEventsUseCase = observeRtcCallEventsUseCase
        let observeRtcSignalsUseCase = observeRtcSignalsUseCase
        let chatRoomId = chatRoomId

        hasSignalConnectedOnce = false
        signalConnected = false
        latestPeerIds = []
        knownPeerIds = []
        callCancellable = KotlinFlowPublisher<RtcCallEvent> { onEach in
            observeRtcCallEventsUseCase.directEventsFlow(chatRoomId: chatRoomId).subscribe(onEach: onEach)
        }
        .sink { [weak self] in self?.handleCallEvent($0) }
        signalCancellable = KotlinFlowPublisher<RtcSignalEvent> { onEach in
            observeRtcSignalsUseCase.directEventsFlow(chatRoomId: chatRoomId).subscribe(onEach: onEach)
        }
        .sink { [weak self] in self?.handleSignalEvent($0) }
    }

    private func handleCallEvent(_ callEvent: RtcCallEvent) {
        switch callEvent.type {
        case .connected:
            uiState.call.isConnected = true
        // 어느 채널이든 유실이면 메시 전체 해체 — 재연결 후 PEERS 전체 목록으로 재구축(D7)
        case .disconnected:
            uiState.call.isConnected = false
            tearDownMesh()
        case .peers:
            uiState.call.peers = callEvent.peers
            latestPeerIds = Set(callEvent.peers.map(\.userId).filter { $0 != uiState.myUserId })
            reconcileMesh()
        default:
            break
        }
    }

    private func handleSignalEvent(_ signalEvent: RtcSignalEvent) {
        switch signalEvent.type {
        case .connected:
            signalConnected = true
            if !hasSignalConnectedOnce {
                hasSignalConnectedOnce = true
                // 발신이면 벨울림 — 시그널 세션이 살아있는 최초 연결 시점에 1회(재연결 때 다시 울리지 않는다).
                // 그룹 방은 서버가 방 멤버 전원에게 팬아웃하고, 진행 중 통화 합류면 서버 게이트가 걸러준다
                if ring {
                    let sendCallInviteUseCase = sendCallInviteUseCase
                    let chatRoomId = chatRoomId

                    Task { @MainActor in
                        try? await sendCallInviteUseCase.invoke(chatRoomId: chatRoomId)
                    }
                }
            }
            reconcileMesh()
        case .disconnected:
            signalConnected = false
            tearDownMesh()
        case .signal:
            guard let session = mediaSession,
                  let fromUserId = signalEvent.fromUserId?.int64Value,
                  let signalType = signalEvent.signalType,
                  let payload = signalEvent.payload
            else { return }

            // 로스터보다 신호가 먼저 닿을 수 있다 — OFFER를 받으면 수신측 피어를 즉석 생성(Compose 미러)
            if signalType == .offer, !knownPeerIds.contains(fromUserId) {
                session.createPeer(peerId: fromUserId, initiator: false)
                knownPeerIds.insert(fromUserId)
            }
            session.applySignal(fromUserId: fromUserId, type: signalType, payload: payload)
        default:
            break
        }
    }

    /// PEERS 스냅숏과 아는 피어의 차집합으로 메시를 맞춘다 — 시그널 채널이 살아 있어야
    /// offer가 유실되지 않으므로 signalConnected 전에는 미룬다(연결되는 순간 재호출).
    private func reconcileMesh() {
        guard let session = mediaSession, signalConnected, let myUserId = uiState.myUserId else { return }

        latestPeerIds.subtracting(knownPeerIds).forEach { peerId in
            // 글레어 방지 — 양쪽이 같은 규칙을 보므로 동시 offer가 없다(웹 D5 미러)
            session.createPeer(peerId: peerId, initiator: myUserId < peerId)
        }
        knownPeerIds.subtracting(latestPeerIds).forEach { session.closePeer(peerId: $0) }
        knownPeerIds = latestPeerIds
    }

    /// 메시 해체(로컬 미디어 유지) — 시그널링을 잃었을 때. 재연결 → PEERS 재수신이 재구축한다
    private func tearDownMesh() {
        mediaSession?.closeAllPeers()
        knownPeerIds = []
    }

    private func relaySignal(_ signal: RtcMediaSession.OutgoingSignal) {
        let sendRtcSignalUseCase = sendRtcSignalUseCase
        let chatRoomId = chatRoomId

        Task { @MainActor in
            try? await sendRtcSignalUseCase.invoke(
                room: RtcRoom(kind: .direct, id: chatRoomId),
                type: signal.type,
                toUserId: signal.toUserId,
                payload: signal.payload
            )
        }
    }

    init(
        chatRoomId: Int64,
        ring: Bool,
        observeRtcCallEventsUseCase: ObserveRtcCallEventsUseCase,
        observeRtcSignalsUseCase: ObserveRtcSignalsUseCase,
        sendRtcSignalUseCase: SendRtcSignalUseCase,
        sendCallInviteUseCase: SendCallInviteUseCase,
        getIceServersUseCase: GetIceServersUseCase,
        getCurrentUserIdUseCase: GetCurrentUserIdUseCase
    ) {
        self.chatRoomId = chatRoomId
        self.ring = ring
        self.observeRtcCallEventsUseCase = observeRtcCallEventsUseCase
        self.observeRtcSignalsUseCase = observeRtcSignalsUseCase
        self.sendRtcSignalUseCase = sendRtcSignalUseCase
        self.sendCallInviteUseCase = sendCallInviteUseCase
        self.getIceServersUseCase = getIceServersUseCase
        uiState = UiState(myUserId: getCurrentUserIdUseCase.invoke()?.int64Value, isRinging: ring)
    }

    // 화면 이탈(pop) — 구독을 닫아 통화에서 나가고 네이티브 미디어를 해제한다
    deinit {
        callCancellable?.cancel()
        signalCancellable?.cancel()
        mediaSession?.dispose()
    }

    /// 통화 상태 — Kotlin RtcCallController.State 미러(트랙 핸들은 RTCVideoTrack 직접 보관)
    struct CallState {
        var isInCall = false
        // isInCall 중 로스터 토픽 소켓이 살아있는지 — 끊기면 "재연결 중" 표시용
        var isConnected = false
        var peers: [RtcCallPeer] = []
        // 미디어 권한 거부면 false — 로스터 전용 통화
        var isMediaActive = false
        var micOn = true
        var camOn = true
        var localVideoTrack: RTCVideoTrack? = nil
        var remoteVideoTracks: [Int64: RTCVideoTrack] = [:]
    }

    struct UiState {
        var myUserId: Int64? = nil
        /// 발신 여부 — 상대가 아직 안 들어왔을 때 "응답 대기" 표시용
        var isRinging = false
        var isJoining = false
        var call = CallState()

        /// PEERS는 본인 포함 전체 목록 — 나뿐이면 아직 아무도 통화에 없다
        var isAloneInCall: Bool { call.isInCall && call.peers.count <= 1 }
    }

    enum Action {
        /// withMedia=false는 권한 거부 시 로스터 전용 참가
        case join(withMedia: Bool)
        case hangUp
        case toggleMic
        case toggleCam
    }

    enum Event {
        /// 통화 종료 — 화면이 pop한다
        case ended
    }
}
