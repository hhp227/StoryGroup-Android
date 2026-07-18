package kr.hhp227.storygroup.shared.data.storage

import kr.hhp227.storygroup.shared.domain.model.AuthTokens
import platform.Foundation.NSUserDefaults

// MVP: NSUserDefaults 저장. 출시 전 Keychain 전환 예정.
class UserDefaultsTokenStorage(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults
) : TokenStorage {

    override fun load(): AuthTokens? {
        val accessToken = defaults.stringForKey(KEY_ACCESS_TOKEN) ?: return null
        val refreshToken = defaults.stringForKey(KEY_REFRESH_TOKEN) ?: return null
        return AuthTokens(accessToken, refreshToken)
    }

    override fun save(tokens: AuthTokens) {
        defaults.setObject(tokens.accessToken, forKey = KEY_ACCESS_TOKEN)
        defaults.setObject(tokens.refreshToken, forKey = KEY_REFRESH_TOKEN)
    }

    override fun clear() {
        defaults.removeObjectForKey(KEY_ACCESS_TOKEN)
        defaults.removeObjectForKey(KEY_REFRESH_TOKEN)
    }

    private companion object {
        const val KEY_ACCESS_TOKEN = "storygroup_access_token"
        const val KEY_REFRESH_TOKEN = "storygroup_refresh_token"
    }
}
