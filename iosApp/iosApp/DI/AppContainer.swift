import Foundation
import Shared

/// shared di/AppContainer.kt의 단일 전역 인스턴스 — 수동 Swift 미러(263줄) 대체.
/// Kotlin 기본 인자는 ObjC로 내보내지지 않아 4개 인자를 전부 명시 전달한다.
/// static let = lazy + thread-safe 1회 초기화(기존 iOSApp.init 시점 생성과 동등).
extension AppContainer {
    static let shared = AppContainer(
        tokenStorage: UserDefaultsTokenStorage(defaults: .standard),
        // 현행 iOS는 settingsStorage 미사용(테마는 SGThemeState가 UserDefaults 직접) — Kotlin 기본값과 동일
        settingsStorage: InMemoryKeyValueStorage(),
        imageCompressor: IosImageCompressor(),
        networkStatusDataSource: IosNetworkStatusDataSource()
    )
}
