import SwiftUI

/// 루트 — Compose App.kt 미러. 테마 계산 후 세션 상태에 따라 인증 플로우/메인 쉘을 라우팅한다.
struct AppRootView: View {
    @StateObject private var theme = SGThemeState()

    @StateObject private var loginViewModel = LoginViewModel()

    @StateObject private var networkStatusViewModel = NetworkStatusViewModel()

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
        // 네트워크 배너 — 로그인 화면 포함 전역 오버레이(composeApp App.kt Box 미러)
        ZStack(alignment: .top) {
            Group {
                if loginViewModel.uiState.isLoggedIn {
                    MainShellView(theme: theme, onLogout: {
                        // 토큰 해제는 best effort — 실패해도 로그아웃은 진행한다(설계 §10)
                        PushRegistrar.unregisterCurrentToken()
                        loginViewModel.onAction(.logout)
                    })
                } else {
                    AuthFlowView(loginViewModel: loginViewModel)
                }
            }
            if networkStatusViewModel.uiState.networkAlertState.isVisible {
                NetworkStatusBannerView(
                    message: networkStatusViewModel.uiState.networkAlertState.message,
                    isConnected: networkStatusViewModel.uiState.networkAlertState.isConnected
                )
                .transition(.move(edge: .top).combined(with: .opacity))
                .zIndex(1)
            }
        }
        .animation(
            .easeInOut(duration: 0.2),
            value: networkStatusViewModel.uiState.networkAlertState.isVisible
        )
        // Compose CompositionLocalProvider(LocalSgColors provides sg) 미러 — 하위 전체에 테마 전파
        .environment(\.sgColors, colors)
        .preferredColorScheme(theme.nightMode == .system ? nil : (isDark ? .dark : .light))
    }

}

/// 로그인 ↔ 가입 전환 — Compose AuthFlow 미러. 가입 성공 시 안내 문구와 함께 로그인으로 복귀.
struct AuthFlowView: View {
    @ObservedObject var loginViewModel: LoginViewModel

    @State private var showRegister = false

    @State private var justRegistered = false

    var body: some View {
        if showRegister {
            RegisterView(
                onRegistered: {
                    justRegistered = true
                    showRegister = false
                },
                onNavigateToLogin: { showRegister = false }
            )
        } else {
            LoginView(
                loginViewModel: loginViewModel,
                justRegistered: justRegistered,
                onNavigateToRegister: {
                    loginViewModel.onAction(.clearError)
                    justRegistered = false
                    showRegister = true
                }
            )
        }
    }
}
