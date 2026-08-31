import UIKit
import UserNotifications
import FirebaseCore
import FirebaseMessaging

/// APNs·FCM 진입점 — 표시는 서버 ApnsConfig alert가 담당하고(설계 §4·§7),
/// 여기선 등록·포그라운드 억제·탭 라우팅만 한다.
/// composeApp의 FirebaseMessagingService(+ MainActivity 딥링크 처리) 미러.
final class AppDelegate: NSObject, UIApplicationDelegate, MessagingDelegate, UNUserNotificationCenterDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // ⚠️ GoogleService-Info.plist가 번들에 없으면 여기서 크래시한다 — Mac 체크리스트 1순위
        FirebaseApp.configure()
        UNUserNotificationCenter.current().delegate = self
        Messaging.messaging().delegate = self

        // 권한은 앱 시작 시 1회 요청(거부해도 앱은 정상 동작 — 토큰만 안 온다)
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
            guard granted else { return }
            DispatchQueue.main.async { application.registerForRemoteNotifications() }
        }
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        // 스위즐링이 켜져 있으면 Firebase가 이미 넣지만, 명시 대입은 멱등이라 무해하다
        Messaging.messaging().apnsToken = deviceToken
    }

    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let token = fcmToken else { return }
        PushRegistrar.currentToken = token
        // 미로그인이면 401로 실패 — 무해하다(로그인 진입 시 MainShellView.task가 재등록한다)
        PushRegistrar.registerCurrentToken()
    }

    /// 포그라운드 억제 — 인앱 STOMP가 이미 표시를 담당한다(설계 §7)
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([])
    }

    /// 백그라운드·종료 상태에서 알림 탭 — 라우팅은 세션 셸이 소비한다(콜드 스타트면 로그인 후 소비)
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        PendingPushRoute.shared.set(from: response.notification.request.content.userInfo)
        completionHandler()
    }
}
