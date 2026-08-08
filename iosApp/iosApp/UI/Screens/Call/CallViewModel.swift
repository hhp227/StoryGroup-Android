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

    /// 발신 무응답 타이머 — 상대가 한 번이라도 PEERS에 들어오면 해제(중도 이탈에 재시작 없음)
    private var noAnswerTask: Task<Void, Never>?

    func onAction(_ action: Action) {
        switch action {
        case .join(let withMedia): join(withMedia: withMedia)
        case .hangUp: hangUp()
        case .toggleMic: toggleMic()
        case .toggleCam: toggleCam()
        case .switchCamera: switchCamera()
        case .toggleSpeaker: toggleSpeaker()
        case .toggleScreenShare: toggleScreenShare()
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
            // 발신이면 무응답 타이머 — 수락/거절 시그널이 없어(D6) PEERS 합류가 유일한 응답 신호다
            if ring { startNoAnswerTimer() }
        }
    }

    /// 끊기 — 구독 취소가 곧 퇴장. 별도 종료 시그널은 없다(상대 화면엔 PEERS 축소로 반영)
    private func hangUp() {
        noAnswerTask?.cancel()
        leave()
        event.send(.ended)
    }

    /// 발신 무응답 자동 종료 — 참가 후 30초 동안 혼자면 "응답 없음" 안내를 잠깐 보여주고 끊는다
    /// (수신 배너의 30초 자동 소거와 대칭 — 벨울림은 휘발 신호라 이 즈음이면 상대 배너도 사라졌다).
    private func startNoAnswerTimer() {
        noAnswerTask?.cancel()
        noAnswerTask = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: Self.noAnswerTimeoutNanos)
            guard let self, !Task.isCancelled else { return }

            self.uiState.isNoAnswer = true
            // 즉시 pop하면 종료 사유를 알 수 없다 — 안내가 보일 짬을 두고 끊는다
            try? await Task.sleep(nanoseconds: Self.noAnswerNoticeNanos)
            if Task.isCancelled { return }
            self.leave()
            self.event.send(.ended)
        }
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
        // 공유 중엔 잠금(D9) — 전송 중인 비디오가 카메라가 아니라 화면이다(웹·Compose 미러)
        if uiState.call.sharing { return }

        uiState.call.camOn.toggle()
        mediaSession?.setCamEnabled(uiState.call.camOn)
    }

    private func switchCamera() {
        // 공유 중엔 잠금 — 로컬 표시가 화면 트랙이라 전환이 보이지 않는다(버튼도 함께 숨김)
        if uiState.call.sharing { return }

        mediaSession?.switchCamera()
    }

    private func toggleSpeaker() {
        uiState.call.speakerOn.toggle()
        mediaSession?.setSpeakerEnabled(uiState.call.speakerOn)
    }

    /// 화면 공유 토글 — 시스템 동의 창이 없는 대신 ReplayKit이 시작 시점에 허가를 묻는다
    /// (Compose는 MediaProjection 동의 토큰이 필요해 Start/Stop 액션이 갈라진 것과 같은 D9 경로)
    private func toggleScreenShare() {
        if uiState.call.sharing {
            mediaSession?.stopScreenShare()
        } else {
            mediaSession?.startScreenShare()
        }
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
        session.onScreenSharing = { [weak self] sharing in self?.uiState.call.sharing = sharing }
        session.onCameraFacing = { [weak self] front in self?.uiState.call.frontCamera = front }
        session.setMicEnabled(uiState.call.micOn)
        session.setCamEnabled(uiState.call.camOn)
        session.setSpeakerEnabled(uiState.call.speakerOn)
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
            // 상대가 한 번이라도 PEERS에 들어오면 무응답 타이머 해제 — 중도 이탈엔 재시작하지
            // 않는다(무응답 전용). 30초 경계에서 뒤늦게 받았으면 안내도 함께 걷는다(Compose 미러)
            if noAnswerTask != nil, callEvent.peers.contains(where: { $0.userId != uiState.myUserId }) {
                noAnswerTask?.cancel()
                noAnswerTask = nil
                uiState.isNoAnswer = false
            }
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
        video: Bool,
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
        // 보이스톡(video=false)은 카메라·스피커폰 OFF로 시작 — 이후엔 토글 소관(Compose 미러)
        uiState.call.camOn = video
        uiState.call.speakerOn = video
    }

    // 화면 이탈(pop) — 구독을 닫아 통화에서 나가고 네이티브 미디어를 해제한다
    deinit {
        callCancellable?.cancel()
        signalCancellable?.cancel()
        noAnswerTask?.cancel()
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
        // 전면 카메라 여부 — 로컬 미리보기 거울용(Compose는 핸들에 mirror가 실려 별도 필드 없음)
        var frontCamera = true
        // 스피커폰 출력 — 페이스톡은 기본 ON, 보이스톡은 수화구(웹엔 없는 모바일 전용, 라우팅은 미디어 세션 소관)
        var speakerOn = true
        // 화면 공유 중 — 공유 중엔 localVideoTrack이 화면 트랙이고 카메라 토글은 잠긴다(웹 D9)
        var sharing = false
        var localVideoTrack: RTCVideoTrack? = nil
        var remoteVideoTracks: [Int64: RTCVideoTrack] = [:]
    }

    struct UiState {
        var myUserId: Int64? = nil
        /// 발신 여부 — 상대가 아직 안 들어왔을 때 "응답 대기" 표시용
        var isRinging = false
        var isJoining = false
        /// 발신 무응답 — 30초 동안 혼자면 true(안내 표시), 잠시 뒤 ended로 pop된다
        var isNoAnswer = false
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
        /// 전/후면 카메라 전환 — 카메라가 있을 때만 버튼이 보인다(공유 중엔 숨김)
        case switchCamera
        /// 스피커폰 토글 — 라우팅은 미디어 세션 소관(웹엔 없는 모바일 전용)
        case toggleSpeaker
        /// 화면 공유 토글 — 오디오 전용(video sender 없음)이면 버튼 자체가 숨겨진다(D9)
        case toggleScreenShare
    }

    enum Event {
        /// 통화 종료 — 화면이 pop한다
        case ended
    }

    /// 수신 배너 ringTimeout(30초)과 대칭 — 이 즈음이면 상대 배너도 이미 사라졌다
    private static let noAnswerTimeoutNanos: UInt64 = 30_000_000_000

    /// "응답 없음" 안내가 보일 최소 시간
    private static let noAnswerNoticeNanos: UInt64 = 1_500_000_000
}
