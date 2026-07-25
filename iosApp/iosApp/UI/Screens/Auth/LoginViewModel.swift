import Foundation
import Shared

/// 세션 홀더 — composeApp LoginViewModel.kt와 1:1 미러(같은 UiState·Action·로직, shared 유스케이스 소비).
/// shared suspend 함수는 메인 스레드에서만 호출 가능하므로 Task { @MainActor in }로 감싼다.
final class LoginViewModel: MviViewModel {
    // 세션 전환은 일회성이 아니라 상태(isLoggedIn) — 이벤트 없음
    typealias Event = Never

    @Published private(set) var uiState: UiState

    private let loginUseCase: LoginUseCase

    private let logoutUseCase: LogoutUseCase

    func onAction(_ action: Action) {
        switch action {
        case .login(let email, let password): login(email: email, password: password)
        case .logout: logout()
        case .clearError: uiState.error = nil
        }
    }

    private func login(email: String, password: String) {
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

    private func logout() {
        Task { @MainActor in
            try? await logoutUseCase.invoke()
            uiState.isLoggedIn = false
        }
    }

    init(
        isLoggedInUseCase: IsLoggedInUseCase,
        loginUseCase: LoginUseCase,
        logoutUseCase: LogoutUseCase
    ) {
        self.loginUseCase = loginUseCase
        self.logoutUseCase = logoutUseCase
        uiState = UiState(isLoggedIn: isLoggedInUseCase.invoke())
    }

    struct UiState {
        var isLoading = false
        var isLoggedIn = false
        var error: String? = nil
    }

    enum Action {
        case login(email: String, password: String)
        case logout
        case clearError
    }
}

extension Error {
    /// Kotlin `e.message ?: fallback` 미러 — shared 예외는 NSError localizedDescription으로 메시지가 건너온다
    func kotlinMessage(fallback: String) -> String {
        let message = (self as NSError).localizedDescription
        return message.isEmpty ? fallback : message
    }
}
