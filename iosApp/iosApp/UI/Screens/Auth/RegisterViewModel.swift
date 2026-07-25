import Combine
import Foundation
import Shared

/// 가입 — composeApp RegisterViewModel.kt와 1:1 미러.
/// 가입 성공 시 웹과 동일하게 로그인 화면으로 돌려보낸다(자동 로그인 안 함) — Event.registered 일회성 발화.
final class RegisterViewModel: MviViewModel {
    @Published private(set) var uiState = UiState()

    let event = PassthroughSubject<Event, Never>()

    private let registerUseCase: RegisterUseCase

    func onAction(_ action: Action) {
        switch action {
        case .register(let name, let email, let password): register(name: name, email: email, password: password)
        case .clearError: uiState.error = nil
        }
    }

    private func register(name: String, email: String, password: String) {
        if uiState.isLoading { return }

        uiState.isLoading = true
        uiState.error = nil
        Task { @MainActor in
            do {
                _ = try await registerUseCase.invoke(name: name, email: email, password: password)
                uiState.isLoading = false
                event.send(.registered)
            } catch {
                uiState.isLoading = false
                uiState.error = error.kotlinMessage(fallback: "가입에 실패했습니다.")
            }
        }
    }

    init(registerUseCase: RegisterUseCase) {
        self.registerUseCase = registerUseCase
    }

    struct UiState {
        var isLoading = false
        var error: String? = nil
    }

    enum Action {
        case register(name: String, email: String, password: String)
        case clearError
    }

    enum Event {
        case registered
    }
}
