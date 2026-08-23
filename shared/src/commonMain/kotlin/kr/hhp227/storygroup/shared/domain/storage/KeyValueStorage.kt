package kr.hhp227.storygroup.shared.domain.storage

/**
 * 앱 설정(테마 등) 영속화 포트 — 도메인 소유, 구현은 data/storage(플랫폼 주입 패턴).
 * (Android=SharedPreferences, Desktop=파일, iOS는 SwiftUI가 UserDefaults를 직접 쓴다)
 */
interface KeyValueStorage {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}
