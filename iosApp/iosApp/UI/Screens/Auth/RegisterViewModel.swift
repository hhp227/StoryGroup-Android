import Foundation
import Shared

/// 가입 — composeApp RegisterViewModel.kt와 1:1 미러.
/// 가입 성공 시 웹과 동일하게 로그인 화면으로 돌려보낸다(자동 로그인 안 함).
final class RegisterViewModel: ObservableObject {
    @Published private(set) var uiState = UiState()

    private let registerUseCase: RegisterUseCase

    func register(name: String, email: String, password: String) {
        if uiState.isLoading { return }

        uiState.isLoading = true
        uiState.error = nil
        Task { @MainActor in
            do {
                _ = try await registerUseCase.invoke(name: name, email: email, password: password)
                uiState.isLoading = false
                uiState.isRegistered = true
            } catch {
                uiState.isLoading = false
                uiState.error = error.kotlinMessage(fallback: "가입에 실패했습니다.")
            }
        }
    }

    /// 가입 완료 이벤트를 소비한다 — 화면 전환 후 재진입 시 중복 발화 방지
    func consumeRegistered() {
        uiState.isRegistered = false
    }

    func clearError() {
        uiState.error = nil
    }

    init(container: AppContainer) {
        registerUseCase = container.registerUseCase
    }

    struct UiState {
        var isLoading = false
        var isRegistered = false
        var error: String? = nil
    }
}
