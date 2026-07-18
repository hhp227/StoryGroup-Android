import Foundation
import Shared

/// 세션 홀더 — composeApp LoginViewModel.kt와 1:1 미러(같은 UiState·로직, shared 유스케이스 소비).
/// shared suspend 함수는 메인 스레드에서만 호출 가능하므로 Task { @MainActor in }로 감싼다.
final class LoginViewModel: ObservableObject {
    @Published private(set) var uiState: UiState

    private let loginUseCase: LoginUseCase

    private let logoutUseCase: LogoutUseCase

    func login(email: String, password: String) {
        if uiState.isLoading { return }

        uiState.isLoading = true
        uiState.error = nil
        Task { @MainActor in
            do {
                _ = try await loginUseCase.invoke(email: email, password: password)
                uiState.isLoading = false
                uiState.isLoggedIn = true
            } catch {
                uiState.isLoading = false
                uiState.error = error.kotlinMessage(fallback: "로그인에 실패했습니다.")
            }
        }
    }

    func logout() {
        Task { @MainActor in
            try? await logoutUseCase.invoke()
            uiState.isLoggedIn = false
        }
    }

    func clearError() {
        uiState.error = nil
    }

    init(container: AppContainer) {
        loginUseCase = container.loginUseCase
        logoutUseCase = container.logoutUseCase
        uiState = UiState(isLoggedIn: container.isLoggedInUseCase.invoke())
    }

    struct UiState {
        var isLoading = false
        var isLoggedIn = false
        var error: String? = nil
    }
}

extension Error {
    /// Kotlin `e.message ?: fallback` 미러 — shared 예외는 NSError localizedDescription으로 메시지가 건너온다
    func kotlinMessage(fallback: String) -> String {
        let message = (self as NSError).localizedDescription
        return message.isEmpty ? fallback : message
    }
}
