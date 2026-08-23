import Combine
import Foundation
import Shared

/// 네트워크 연결 배너 상태 — composeApp NetworkStatusViewModel.kt와 1:1 미러.
/// 사용자 액션·일회성 이벤트가 없어 Action/Event 모두 Never.
final class NetworkStatusViewModel: MviViewModel {
    typealias Action = Never

    typealias Event = Never

    @Published private(set) var uiState = UiState()

    private var cancellables = Set<AnyCancellable>()

    func onAction(_ action: Never) {}

    init(observeNetworkAlertStateUseCase: ObserveNetworkAlertStateUseCase) {
        // 구독 수명 = 루트 VM 수명 — 앱이 살아 있는 동안 감지가 유지된다
        KotlinFlowPublisher<NetworkAlertState> { onEach in
            observeNetworkAlertStateUseCase.statesFlow().subscribe(onEach: onEach)
        }
        .sink { [weak self] state in self?.uiState.networkAlertState = state }
        .store(in: &cancellables)
    }

    struct UiState {
        var networkAlertState: NetworkAlertState = NetworkAlertState.companion.hidden
    }
}
