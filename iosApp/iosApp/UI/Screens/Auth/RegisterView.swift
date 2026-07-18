import SwiftUI

/// 가입 — 웹 /register·Compose RegisterScreen 미러. 성공 시 상위(AuthFlowView)가 로그인으로 되돌린다.
struct RegisterView: View {
    @StateObject private var model: RegisterViewModel
    @Environment(\.sgColors) private var colors
    let onRegistered: () -> Void
    let onNavigateToLogin: () -> Void
    @State private var name = ""
    @State private var email = ""
    @State private var password = ""

    init(
        container: AppContainer,
        onRegistered: @escaping () -> Void,
        onNavigateToLogin: @escaping () -> Void
    ) {
        _model = StateObject(wrappedValue: RegisterViewModel(container: container))
        self.onRegistered = onRegistered
        self.onNavigateToLogin = onNavigateToLogin
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
                    enabled: !model.uiState.isLoading
                )
                Spacer().frame(height: 16)
                SGTextField(
                    label: "이메일",
                    text: $email,
                    keyboard: .emailAddress,
                    enabled: !model.uiState.isLoading
                )
                Spacer().frame(height: 16)
                SGTextField(
                    label: "비밀번호",
                    text: $password,
                    isSecure: true,
                    enabled: !model.uiState.isLoading
                )

                if let error = model.uiState.error {
                    Spacer().frame(height: 12)
                    Text(error).font(.caption).foregroundColor(colors.rust)
                }
                Spacer().frame(height: 24)

                SGPrimaryButton(
                    title: model.uiState.isLoading ? "가입하는 중..." : "가입하기",
                    enabled: !name.isEmpty && !email.isEmpty && !password.isEmpty,
                    isLoading: model.uiState.isLoading,
                    action: {
                        model.register(
                            name: name.trimmingCharacters(in: .whitespaces),
                            email: email.trimmingCharacters(in: .whitespaces),
                            password: password
                        )
                    }
                )
                Spacer().frame(height: 20)

                HStack(spacing: 6) {
                    Text("이미 계정이 있나요?")
                        .font(.subheadline)
                        .foregroundColor(colors.inkSoft)
                    Button(action: onNavigateToLogin) {
                        Text("로그인")
                            .font(.subheadline.bold())
                            .foregroundColor(colors.accent)
                    }
                    .disabled(model.uiState.isLoading)
                }
            }
            .padding(.horizontal, 24)
        }
        .background(colors.paper.ignoresSafeArea())
        .onChange(of: model.uiState.isRegistered) { isRegistered in
            if isRegistered {
                model.consumeRegistered()
                onRegistered()
            }
        }
    }
}
