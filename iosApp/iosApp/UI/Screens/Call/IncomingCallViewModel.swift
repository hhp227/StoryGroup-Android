import Combine
import Foundation
import Shared

/// 수신 통화 배너(DM·그룹 방) — 개인 큐(공유 소켓)의 CALL_INVITE를 세션 전역에서 받아 표시한다
/// (웹 app-header 알림 훅의 수락/거절 배너·composeApp IncomingCallViewModel.kt와 1:1 미러).
/// 벨울림은 DB에 남지 않는 휘발 신호라 일정 시간 뒤 자동으로 사라진다(부재중 이력 없음).
/// 수락 시 화면 이동은 셸(MainShellView) 몫.
final class IncomingCallViewModel: MviViewModel {
    typealias Event = Never

    @Published private(set) var uiState = UiState()

    /// 자동 소거 타이머 — 새 벨울림이 오면 리셋된다
    private var dismissTask: Task<Void, Never>?

    private var cancellables = Set<AnyCancellable>()

    func onAction(_ action: Action) {
        switch action {
        case .dismiss: dismiss()
        }
    }

    private func show(_ event: PersonalEvent) {
        guard let chatRoomId = event.chatRoomId?.int64Value else { return }

        dismissTask?.cancel()
        uiState.incomingCall = IncomingCall(
            chatRoomId: chatRoomId,
            callerId: event.senderId?.int64Value,
            callerName: event.senderName ?? "알 수 없음",
            roomName: event.roomName,
            video: event.video
        )
        dismissTask = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: Self.ringTimeoutNanos)
            if !Task.isCancelled { self?.dismiss() }
        }
    }

    private func dismiss() {
        dismissTask?.cancel()
        dismissTask = nil
        uiState.incomingCall = nil
    }

    init(observePersonalEventsUseCase: ObservePersonalEventsUseCase) {
        // 구독 수명 = 세션 VM(셸) 수명 — 로그아웃으로 셸이 내려가면 함께 정리된다
        KotlinFlowPublisher<PersonalEvent> { onEach in
            observePersonalEventsUseCase.eventsFlow().subscribe(onEach: onEach)
        }
        .sink { [weak self] event in
            if event.type == .callInvite { self?.show(event) }
        }
        .store(in: &cancellables)
    }

    struct UiState {
        var incomingCall: IncomingCall? = nil
    }

    /// 수신 벨울림 — 그 순간에만 의미 있는 휘발 신호(서버 CallInviteEvent 미러)
    struct IncomingCall {
        let chatRoomId: Int64
        let callerId: Int64?
        let callerName: String
        /// 그룹 방 벨울림이면 방(그룹) 이름 — 배너 제목과 통화 화면 제목에 쓴다. DM이면 nil
        let roomName: String?
        /// false면 보이스톡 — 배너 문구와 수락 시 카메라 OFF 입장에 쓴다
        let video: Bool
    }

    enum Action {
        case dismiss
    }

    private static let ringTimeoutNanos: UInt64 = 30_000_000_000
}
