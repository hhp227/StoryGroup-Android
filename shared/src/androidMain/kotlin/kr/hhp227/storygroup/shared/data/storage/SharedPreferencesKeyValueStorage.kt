package kr.hhp227.storygroup.shared.data.storage

import android.content.Context
import android.content.SharedPreferences

class SharedPreferencesKeyValueStorage(context: Context) : KeyValueStorage {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("storygroup_settings", Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}
