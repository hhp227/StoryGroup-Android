import SwiftUI

/// 로그인 — 웹 /login·Compose LoginScreen 미러
struct LoginView: View {
    @ObservedObject var session: LoginViewModel

    let colors: SGColors

    let justRegistered: Bool

    let onNavigateToRegister: () -> Void

    @State private var email = ""

    @State private var password = ""

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Spacer().frame(height: 80)
                Text("다시 만나서 반가워요")
                    .font(.title2.bold())
                    .foregroundColor(colors.ink)
                Spacer().frame(height: 8)
                Text("이메일과 비밀번호로 로그인하세요.")
                    .font(.subheadline)
                    .foregroundColor(colors.inkSoft)
                Spacer().frame(height: 28)
                if justRegistered {
                    SGCard(colors: colors) {
                        Text("가입이 완료됐습니다. 로그인해주세요.")
                            .font(.subheadline)
                            .foregroundColor(colors.moss)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 12)
                    }
                    Spacer().frame(height: 20)
                }
                SGTextField(
                    label: "이메일",
                    text: $email,
                    colors: colors,
                    keyboard: .emailAddress,
                    enabled: !session.uiState.isLoading
                )
                Spacer().frame(height: 16)
                SGTextField(
                    label: "비밀번호",
                    text: $password,
                    colors: colors,
                    isSecure: true,
                    enabled: !session.uiState.isLoading
                )
                if let error = session.uiState.error {
                    Spacer().frame(height: 12)
                    Text(error).font(.caption).foregroundColor(colors.rust)
                }
                Spacer().frame(height: 24)
                SGPrimaryButton(
                    title: session.uiState.isLoading ? "로그인하는 중..." : "로그인",
                    colors: colors,
                    enabled: !email.isEmpty && !password.isEmpty,
                    isLoading: session.uiState.isLoading,
                    action: {
                        session.login(email: email.trimmingCharacters(in: .whitespaces), password: password)
                    }
                )
                Spacer().frame(height: 20)
                HStack(spacing: 6) {
                    Text("아직 계정이 없나요?")
                        .font(.subheadline)
                        .foregroundColor(colors.inkSoft)
                    Button(action: onNavigateToRegister) {
                        Text("가입하기")
                            .font(.subheadline.bold())
                            .foregroundColor(colors.accent)
                    }
                    .disabled(session.uiState.isLoading)
                }
            }
            .padding(.horizontal, 24)
        }
        .background(colors.paper.ignoresSafeArea())
    }
}
