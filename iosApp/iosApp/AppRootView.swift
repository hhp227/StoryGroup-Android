import SwiftUI

/// 루트 — Compose App.kt 미러. 테마 계산 후 세션 상태에 따라 인증 플로우/메인 쉘을 라우팅한다.
struct AppRootView: View {
    let container: AppContainer

    @StateObject private var theme = SGThemeState()

    @StateObject private var session: LoginViewModel

    @Environment(\.colorScheme) private var systemScheme

    private var isDark: Bool {
        switch theme.nightMode {
        case .system: return systemScheme == .dark
        case .light: return false
        case .dark: return true
        }
    }

    private var colors: SGColors { SGColors.palette(mood: theme.mood, dark: isDark) }

    var body: some View {
        Group {
            if session.uiState.isLoggedIn {
                MainShellView(container: container, theme: theme, colors: colors, onLogout: { session.logout() })
            } else {
                AuthFlowView(container: container, session: session, colors: colors)
            }
        }
        .preferredColorScheme(theme.nightMode == .system ? nil : (isDark ? .dark : .light))
    }

    init(container: AppContainer) {
        self.container = container
        _session = StateObject(wrappedValue: LoginViewModel(container: container))
    }
}

/// 로그인 ↔ 가입 전환 — Compose AuthFlow 미러. 가입 성공 시 안내 문구와 함께 로그인으로 복귀.
struct AuthFlowView: View {
    let container: AppContainer

    @ObservedObject var session: LoginViewModel

    let colors: SGColors

    @State private var showRegister = false

    @State private var justRegistered = false

    var body: some View {
        if showRegister {
            RegisterView(
                container: container,
                colors: colors,
                onRegistered: {
                    justRegistered = true
                    showRegister = false
                },
                onNavigateToLogin: { showRegister = false }
            )
        } else {
            LoginView(
                session: session,
                colors: colors,
                justRegistered: justRegistered,
                onNavigateToRegister: {
                    session.clearError()
                    justRegistered = false
                    showRegister = true
                }
            )
        }
    }
}
