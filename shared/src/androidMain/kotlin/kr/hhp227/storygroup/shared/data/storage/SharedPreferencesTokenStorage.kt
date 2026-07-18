package kr.hhp227.storygroup.shared.data.storage

import android.content.Context
import android.content.SharedPreferences
import kr.hhp227.storygroup.shared.domain.model.AuthTokens

class SharedPreferencesTokenStorage(context: Context) : TokenStorage {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("storygroup_auth", Context.MODE_PRIVATE)

    override fun load(): AuthTokens? {
        val accessToken = prefs.getString(KEY_ACCESS_TOKEN, null) ?: return null
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null) ?: return null
        return AuthTokens(accessToken, refreshToken)
    }

    override fun save(tokens: AuthTokens) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, tokens.accessToken)
            .putString(KEY_REFRESH_TOKEN, tokens.refreshToken)
            .apply()
    }

    override fun clear() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .apply()
    }

    private companion object {
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
    }
}
