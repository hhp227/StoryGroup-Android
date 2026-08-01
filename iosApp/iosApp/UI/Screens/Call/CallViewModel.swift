import Combine
import Foundation
import Shared

/// 방 통화(DM 1:1·그룹 방 공용, 페이스톡 미러) — composeApp CallViewModel.kt와 1:1 미러(미디어 제외).
/// 통화 걸기 = rtc 방(chat-rooms/{id}) 로스터 토픽 구독(=입장) + 벨울림(invite) SEND(웹 D6 미러,
/// 그룹 방은 서버가 방 멤버 전원 팬아웃·진행 중 합류엔 안 울림). 벨울림 발신은 시그널 채널 최초
/// 연결 시점 1회 — SEND가 그 소켓의 살아있는 세션을 빌려 쓰기 때문(재연결 때 다시 울리지 않는다).
/// 카메라/마이크 미디어는 네이티브 WebRTC SDK 도입 후속 — 지금은 로스터 전용 참가(Desktop과 동일).
/// 수락/거절 시그널은 없다 — 건 쪽은 상대가 PEERS에 안 들어오면 대기 표시가 계속될 뿐이고,
/// 받는 쪽 거절은 그냥 배너 닫기다.
final class CallViewModel: MviViewModel {
    @Published private(set) var uiState: UiState

    let event = PassthroughSubject<Event, Never>()

    let chatRoomId: Int64

    private let ring: Bool

    private let observeRtcCallEventsUseCase: ObserveRtcCallEventsUseCase

    private let observeRtcSignalsUseCase: ObserveRtcSignalsUseCase

    private let sendCallInviteUseCase: SendCallInviteUseCase

    /// 통화(로스터 토픽) 구독 — 해지(cancel)가 곧 통화 퇴장(Kotlin RtcCallController.leave 대응)
    private var callCancellable: AnyCancellable?

    /// 시그널 채널 구독 — 벨울림 SEND가 이 소켓의 살아있는 세션을 빌려 쓴다(로스터 전용이라 SIGNAL은 무시)
    private var signalCancellable: AnyCancellable?

    /// 발신 벨울림은 시그널 채널 최초 연결에 1회(Kotlin hasSignalConnectedOnce 미러)
    private var hasSignalConnectedOnce = false

    func onAction(_ action: Action) {
        switch action {
        case .join: join()
        case .hangUp: hangUp()
        }
    }

    /// 화면 진입 시 1회 — 바로 입장한다(미디어 없음 — 로스터 전용)
    private func join() {
        if uiState.call.isInCall { return }

        // 구독 클로저는 VM 수명 밖에서도 보관된다 — self 대신 지역 캡처로 순환 참조를 끊는다
        let observeRtcCallEventsUseCase = observeRtcCallEventsUseCase
        let observeRtcSignalsUseCase = observeRtcSignalsUseCase
        let chatRoomId = chatRoomId

        uiState.call = CallState(isInCall: true, isConnected: false, peers: [])
        callCancellable = KotlinFlowPublisher<RtcCallEvent> { onEach in
            observeRtcCallEventsUseCase.directEventsFlow(chatRoomId: chatRoomId).subscribe(onEach: onEach)
        }
        .sink { [weak self] in self?.handleCallEvent($0) }
        signalCancellable = KotlinFlowPublisher<RtcSignalEvent> { onEach in
            observeRtcSignalsUseCase.directEventsFlow(chatRoomId: chatRoomId).subscribe(onEach: onEach)
        }
        .sink { [weak self] in self?.handleSignalEvent($0) }
    }

    /// 끊기 — 구독 취소가 곧 퇴장. 별도 종료 시그널은 없다(상대 화면엔 PEERS 축소로 반영)
    private func hangUp() {
        callCancellable?.cancel()
        callCancellable = nil
        signalCancellable?.cancel()
        signalCancellable = nil
        uiState.call = CallState()
        event.send(.ended)
    }

    private func handleCallEvent(_ callEvent: RtcCallEvent) {
        switch callEvent.type {
        case .connected:
            uiState.call.isConnected = true
        case .disconnected:
            uiState.call.isConnected = false
        case .peers:
            uiState.call.peers = callEvent.peers
        default:
            break
        }
    }

    private func handleSignalEvent(_ signalEvent: RtcSignalEvent) {
        // 발신이면 벨울림 — 시그널 세션이 살아있는 최초 연결 시점에 1회(재연결 때 다시 울리지 않는다).
        // 그룹 방은 서버가 방 멤버 전원에게 팬아웃하고, 진행 중 통화 합류면 서버 게이트가 걸러준다
        if signalEvent.type == .connected, !hasSignalConnectedOnce {
            hasSignalConnectedOnce = true
            if ring {
                let sendCallInviteUseCase = sendCallInviteUseCase
                let chatRoomId = chatRoomId

                Task { @MainActor in
                    try? await sendCallInviteUseCase.invoke(chatRoomId: chatRoomId)
                }
            }
        }
    }

    init(
        chatRoomId: Int64,
        ring: Bool,
        observeRtcCallEventsUseCase: ObserveRtcCallEventsUseCase,
        observeRtcSignalsUseCase: ObserveRtcSignalsUseCase,
        sendCallInviteUseCase: SendCallInviteUseCase,
        getCurrentUserIdUseCase: GetCurrentUserIdUseCase
    ) {
        self.chatRoomId = chatRoomId
        self.ring = ring
        self.observeRtcCallEventsUseCase = observeRtcCallEventsUseCase
        self.observeRtcSignalsUseCase = observeRtcSignalsUseCase
        self.sendCallInviteUseCase = sendCallInviteUseCase
        uiState = UiState(myUserId: getCurrentUserIdUseCase.invoke()?.int64Value, isRinging: ring)
    }

    // 화면 이탈(pop) — 구독을 닫아 통화에서 나간다(Kotlin viewModelScope 취소 대응)
    deinit {
        callCancellable?.cancel()
        signalCancellable?.cancel()
    }

    /// 통화 상태 — Kotlin RtcCallController.State의 로스터 전용 부분집합(미디어 필드 없음)
    struct CallState {
        var isInCall = false
        // isInCall 중 로스터 토픽 소켓이 살아있는지 — 끊기면 "재연결 중" 표시용
        var isConnected = false
        var peers: [RtcCallPeer] = []
    }

    struct UiState {
        var myUserId: Int64? = nil
        /// 발신 여부 — 상대가 아직 안 들어왔을 때 "응답 대기" 표시용
        var isRinging = false
        var call = CallState()

        /// PEERS는 본인 포함 전체 목록 — 나뿐이면 아직 아무도 통화에 없다
        var isAloneInCall: Bool { call.isInCall && call.peers.count <= 1 }
    }

    enum Action {
        case join
        case hangUp
    }

    enum Event {
        /// 통화 종료 — 화면이 pop한다
        case ended
    }
}
