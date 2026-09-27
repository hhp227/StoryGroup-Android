import AuthenticationServices
import GoogleSignIn
import Shared
import SwiftUI

/// "또는" 구분선 + 애플 → 구글(애플 HIG: 애플 버튼을 다른 소셜 버튼보다 덜 눈에 띄게 두지 않는다) — Compose SocialSignInSection 미러.
/// 로그인·가입 화면 공용 — 둘 다 가입=로그인이라 세션 VM(LoginViewModel)으로 보낸다.
/// 버튼은 화면 전용(VM Action·플랫폼 SDK에 묶임)이라 Components가 아닌 이 파일의 private 부품이다(Compose SocialSignInSection.kt와 같은 파일 구성).
/// 애플은 번들 ID만으로 동작해 iOS에선 항상 보인다. 아래 여백은 섹션이 포함한다
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
        AppleSignInButton(loginViewModel: loginViewModel, enabled: enabled)
        Spacer().frame(height: 12)
        if !GoogleAuthConfig.shared.IOS_CLIENT_ID.isEmpty {
            GoogleSignInButton(loginViewModel: loginViewModel, enabled: enabled)
            Spacer().frame(height: 12)
        }
        Spacer().frame(height: 8)
    }
}

/// 애플 HIG 공식 버튼(검정) — 크기·모양은 SGPrimaryButton(48pt, radiusButton)과 같게
private struct AppleSignInButton: View {
    @ObservedObject var loginViewModel: LoginViewModel

    let enabled: Bool

    @Environment(\.sgColors) private var colors

    var body: some View {
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
    }

    /// ASAuthorization 결과 → identityToken(+최초 이름). 취소는 조용히 무시한다
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

/// 구글 — 로그인 버튼과 같은 SGPrimaryButton
private struct GoogleSignInButton: View {
    @ObservedObject var loginViewModel: LoginViewModel

    let enabled: Bool

    var body: some View {
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
