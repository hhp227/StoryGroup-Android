import SwiftUI

@main
struct iOSApp: App {
    /// APNs·FCM 진입점 — SwiftUI 앱에도 UIApplicationDelegate가 필요하다(Push/AppDelegate.swift)
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            AppRootView()
        }
    }
}
