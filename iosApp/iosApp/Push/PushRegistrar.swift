import Foundation
import Shared

/// FCM 토큰 서버 등록/해제 — composeApp onSessionStart/onSessionEnd 미러.
/// 토큰은 MessagingDelegate가 갱신해 두고, 로그인 상태 진입(MainShellView.task)·로그아웃 시 소비한다.
/// ⚠️ 두 UseCase 모두 Kotlin `@Throws suspend operator fun invoke` — ObjC 브리지를 거쳐
/// Swift에는 `try await ...invoke(...)`로 노출된다(LoginViewModel/CallViewModel과 같은 표기).
enum PushRegistrar {
    /// MessagingDelegate가 채워두는 최신 FCM 토큰(미수신이면 nil이라 등록/해제 모두 no-op)
    static var currentToken: String?

    static func registerCurrentToken() {
        guard let token = currentToken else { return }
        // shared suspend는 메인 스레드에서만 호출 가능하다(LoginViewModel 규약) — 호출부(FCM 델리게이트
        // 콜백·View.task)가 둘 다 비격리라 여기서 명시적으로 메인에 올린다
        Task { @MainActor in
            // 실패 무시 — 서버가 멱등(upsert)이라 다음 세션 진입·토큰 갱신에서 재시도한다(설계 §10).
            // ⚠️ PushPlatform은 Kotlin enum의 ObjC 브리지 — 케이스 표기(.ios)는 Mac에서 컴파일 확인
            try? await AppContainer.shared.registerPushTokenUseCase.invoke(
                token: token,
                platform: PushPlatform.ios
            )
        }
    }

    static func unregisterCurrentToken() {
        guard let token = currentToken else { return }
        // 위와 같은 이유로 메인 격리(로그아웃 클로저도 비격리다)
        Task { @MainActor in
            // best effort — 실패해도 로그아웃은 진행한다(설계 §10)
            try? await AppContainer.shared.unregisterPushTokenUseCase.invoke(token: token)
        }
    }
}
