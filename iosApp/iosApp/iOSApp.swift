import SwiftUI

@main
struct iOSApp: App {
    private let container = AppContainer()

    var body: some Scene {
        WindowGroup {
            AppRootView(container: container)
        }
    }
}
