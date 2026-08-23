package kr.hhp227.storygroup.shared.data.storage

import kr.hhp227.storygroup.shared.domain.storage.KeyValueStorage

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
