import AuthenticationServices
import GoogleSignIn
import Shared
import SwiftUI

/// 로그인 — 웹 /login·Compose LoginScreen 미러. 액션은 Action으로 올린다(MVI)
struct LoginView: View {
    @ObservedObject var loginViewModel: LoginViewModel

    @Environment(\.sgColors) private var colors

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
                    SGCard {
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
                    keyboard: .emailAddress,
                    enabled: !loginViewModel.uiState.isLoading
                )
                Spacer().frame(height: 16)
                SGTextField(
                    label: "비밀번호",
                    text: $password,
                    isSecure: true,
                    enabled: !loginViewModel.uiState.isLoading
                )
                if let error = loginViewModel.uiState.error {
                    Spacer().frame(height: 12)
                    Text(error).font(.caption).foregroundColor(colors.rust)
                }
                Spacer().frame(height: 24)
                SGPrimaryButton(
                    title: loginViewModel.uiState.isLoading ? "로그인하는 중..." : "로그인",
                    enabled: !email.isEmpty && !password.isEmpty,
                    isLoading: loginViewModel.uiState.isLoading,
                    action: {
                        loginViewModel.onAction(.login(email: email.trimmingCharacters(in: .whitespaces), password: password))
                    }
                )
                Spacer().frame(height: 20)
                SocialSignInSection(loginViewModel: loginViewModel, enabled: !loginViewModel.uiState.isLoading)
                HStack(spacing: 6) {
                    Text("아직 계정이 없나요?")
                        .font(.subheadline)
                        .foregroundColor(colors.inkSoft)
                    Button(action: onNavigateToRegister) {
                        Text("가입하기")
                            .font(.subheadline.bold())
                            .foregroundColor(colors.accent)
                    }
                    .disabled(loginViewModel.uiState.isLoading)
                }
            }
            .padding(.horizontal, 24)
        }
        .background(colors.paper.ignoresSafeArea())
    }
}

/// "또는" 구분선 + 애플 → 구글(Compose SocialSignInSection 미러). 애플은 번들 ID만으로 동작해 iOS에선 항상 보인다
struct SocialSignInSection: View {
    @ObservedObject var loginViewModel: LoginViewModel

    let enabled: Bool

    @Environment(\.sgColors) private var colors

    var body: some View {
        HStack(spacing: 12) {
            Rectangle().fill(colors.stoneBorder).frame(height: 1)
            Text("또는").font(.caption).foregroundColor(colors.inkSoft)
            Rectangle().fill(colors.stoneBorder).frame(height: 1)
        }
        Spacer().frame(height: 20)
        SignInWithAppleButton(.continue) { request in
            request.requestedScopes = [.fullName, .email]
        } onCompletion: { result in
            handleApple(result)
        }
        .signInWithAppleButtonStyle(.black)
        .frame(height: 48)
        .clipShape(RoundedRectangle(cornerRadius: colors.radiusButton ?? 24, style: .continuous))
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.5)
        Spacer().frame(height: 12)
        GoogleSignInSection(loginViewModel: loginViewModel, enabled: enabled)
    }

    private func handleApple(_ result: Result<ASAuthorization, Error>) {
        switch result {
        case .success(let authorization):
            guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
                  let tokenData = credential.identityToken,
                  let identityToken = String(data: tokenData, encoding: .utf8)
            else {
                loginViewModel.onAction(.appleLoginFailed(message: nil))
                return
            }
            let code = credential.authorizationCode.flatMap { String(data: $0, encoding: .utf8) }
            // 이름은 최초 인가 1회만 온다 — 이후엔 nil
            loginViewModel.onAction(.appleLogin(
                identityToken: identityToken,
                authorizationCode: code,
                firstName: credential.fullName?.givenName,
                lastName: credential.fullName?.familyName
            ))
        case .failure(let error):
            if (error as? ASAuthorizationError)?.code == .canceled { return }
            loginViewModel.onAction(.appleLoginFailed(message: error.localizedDescription))
        }
    }
}

/// 구글 버튼(로그인 버튼과 같은 SGPrimaryButton) — SocialSignInSection이 구분선·애플 버튼 아래에 배치한다(Compose GoogleSignInButton 미러).
/// 구글은 가입과 로그인이 한 경로라 두 화면 모두 세션 VM(LoginViewModel)으로 보낸다.
/// iOS 클라이언트 ID가 없으면 섹션 자체를 그리지 않는다. 아래 여백은 섹션이 포함한다
struct GoogleSignInSection: View {
    @ObservedObject var loginViewModel: LoginViewModel

    let enabled: Bool

    @Environment(\.sgColors) private var colors

    var body: some View {
        if !GoogleAuthConfig.shared.IOS_CLIENT_ID.isEmpty {
            SGPrimaryButton(
                title: "Google로 계속하기",
                enabled: enabled,
                // 구글 브랜드 4색 G(Assets GoogleG, 벡터) — 핑크 버튼 위에서도 보이도록 흰 원 배지(Compose GoogleLogo 미러)
                leading: AnyView(
                    Image("GoogleG")
                        .resizable()
                        .frame(width: 14, height: 14)
                        .frame(width: 22, height: 22)
                        .background(Circle().fill(Color.white))
                ),
                action: startGoogleSignIn
            )
            Spacer().frame(height: 20)
        }
    }

    /// GIDSignIn 시트 → ID 토큰(aud=iOS 클라이언트, 백엔드 허용 목록에 포함). 취소는 조용히 무시한다
    private func startGoogleSignIn() {
        guard let root = UIApplication.shared.connectedScenes
            .compactMap({ ($0 as? UIWindowScene)?.windows.first(where: { $0.isKeyWindow })?.rootViewController })
            .first
        else { return }
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: GoogleAuthConfig.shared.IOS_CLIENT_ID)
        GIDSignIn.sharedInstance.signIn(withPresenting: root) { result, error in
            if let error = error as NSError? {
                if error.domain == kGIDSignInErrorDomain && error.code == GIDSignInError.canceled.rawValue { return }
                loginViewModel.onAction(.googleLoginFailed(message: error.localizedDescription))
                return
            }
            guard let idToken = result?.user.idToken?.tokenString else {
                loginViewModel.onAction(.googleLoginFailed(message: nil))
                return
            }
            loginViewModel.onAction(.googleLogin(idToken: idToken))
        }
    }
}
