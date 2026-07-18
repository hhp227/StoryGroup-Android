package kr.hhp227.storygroup.shared.data.storage

/**
 * 앱 설정(테마 등) 영속화용 단순 키-값 저장소 — TokenStorage와 같은 플랫폼 주입 패턴.
 * (Android=SharedPreferences, Desktop=파일, iOS는 SwiftUI가 UserDefaults를 직접 쓴다)
 */
interface KeyValueStorage {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}

class InMemoryKeyValueStorage : KeyValueStorage {
    private val values = mutableMapOf<String, String>()

    override fun getString(key: String): String? = values[key]

    override fun putString(key: String, value: String) {
        values[key] = value
    }

    override fun remove(key: String) {
        values.remove(key)
    }
}
