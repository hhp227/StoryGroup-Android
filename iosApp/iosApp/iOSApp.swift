import GoogleSignIn
import SwiftUI

@main
struct iOSApp: App {
    /// APNs·FCM 진입점 — SwiftUI 앱에도 UIApplicationDelegate가 필요하다(Push/AppDelegate.swift)
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            AppRootView()
                // GIDSignIn 인증 후 앱 복귀 URL(reversed client ID scheme) 처리
                .onOpenURL { url in
                    GIDSignIn.sharedInstance.handle(url)
                }
        }
    }
}
