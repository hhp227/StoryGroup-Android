import SwiftUI

/// 가입 — 웹 /register·Compose RegisterScreen 미러. 성공 시 상위(AuthFlowView)가 로그인으로 되돌린다.
struct RegisterView: View {
    @StateObject private var registerViewModel = RegisterViewModel()

    /// 구글은 가입=로그인 — 성공하면 세션 VM의 isLoggedIn이 바뀌어 루트가 바로 세션 화면으로 넘어간다
    @ObservedObject var loginViewModel: LoginViewModel
    
    @Environment(\.sgColors) private var colors
    
    let onRegistered: () -> Void
    
    let onNavigateToLogin: () -> Void
    
    @State private var name = ""
    
    @State private var email = ""
    
    @State private var password = ""

    private var isBusy: Bool {
        registerViewModel.uiState.isLoading || loginViewModel.uiState.isLoading
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Spacer().frame(height: 80)
                Text("같이할 사람들을 위한 자리")
                    .font(.title2.bold())
                    .foregroundColor(colors.ink)
                Spacer().frame(height: 8)
                Text("StoryGroup에 가입하고 그룹을 만들어보세요.")
                    .font(.subheadline)
                    .foregroundColor(colors.inkSoft)
                Spacer().frame(height: 28)
                SGTextField(
                    label: "이름",
                    text: $name,
                    enabled: !registerViewModel.uiState.isLoading
                )
                Spacer().frame(height: 16)
                SGTextField(
                    label: "이메일",
                    text: $email,
                    keyboard: .emailAddress,
                    enabled: !registerViewModel.uiState.isLoading
                )
                Spacer().frame(height: 16)
                SGTextField(
                    label: "비밀번호",
                    text: $password,
                    isSecure: true,
                    enabled: !registerViewModel.uiState.isLoading
                )
                if let error = registerViewModel.uiState.error {
                    Spacer().frame(height: 12)
                    Text(error).font(.caption).foregroundColor(colors.rust)
                }
                Spacer().frame(height: 24)
                SGPrimaryButton(
                    title: registerViewModel.uiState.isLoading ? "가입하는 중..." : "가입하기",
                    enabled: !name.isEmpty && !email.isEmpty && !password.isEmpty,
                    isLoading: registerViewModel.uiState.isLoading,
                    action: {
                        registerViewModel.onAction(.register(
                            name: name.trimmingCharacters(in: .whitespaces),
                            email: email.trimmingCharacters(in: .whitespaces),
                            password: password
                        ))
                    }
                )
                Spacer().frame(height: 20)
                GoogleSignInSection(loginViewModel: loginViewModel, enabled: !isBusy)
                if let googleError = loginViewModel.uiState.error {
                    Text(googleError).font(.caption).foregroundColor(colors.rust)
                    Spacer().frame(height: 20)
                }
                HStack(spacing: 6) {
                    Text("이미 계정이 있나요?")
                        .font(.subheadline)
                        .foregroundColor(colors.inkSoft)
                    Button(action: {
                        // 구글 에러가 로그인 화면으로 새지 않게 정리하고 떠난다
                        loginViewModel.onAction(.clearError)
                        onNavigateToLogin()
                    }) {
                        Text("로그인")
                            .font(.subheadline.bold())
                            .foregroundColor(colors.accent)
                    }
                    .disabled(isBusy)
                }
            }
            .padding(.horizontal, 24)
        }
        .background(colors.paper.ignoresSafeArea())
        // 일회성 이벤트 수신 — Compose AuthFlow의 event.collect 미러
        .onReceive(registerViewModel.event) { event in
            switch event {
            case .registered: onRegistered()
            }
        }
    }
    
    init(
        onRegistered: @escaping () -> Void,
        onNavigateToLogin: @escaping () -> Void
    ) {
        self.onRegistered = onRegistered
        self.onNavigateToLogin = onNavigateToLogin
    }
}
