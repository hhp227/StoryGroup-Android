package kr.hhp227.storygroup.shared.data.storage

import java.io.File
import java.util.Properties
import kr.hhp227.storygroup.shared.domain.model.AuthTokens

/** Desktop(JVM)용 토큰 저장소 — Android SharedPreferences/iOS UserDefaults와 같은 역할 */
class FileTokenStorage(
    private val file: File = File(System.getProperty("user.home"), ".storygroup/auth.properties")
) : TokenStorage {

    override fun load(): AuthTokens? {
        if (!file.exists()) return null
        val props = Properties().apply { file.inputStream().use(::load) }
        val accessToken = props.getProperty(KEY_ACCESS_TOKEN) ?: return null
        val refreshToken = props.getProperty(KEY_REFRESH_TOKEN) ?: return null
        return AuthTokens(accessToken, refreshToken)
    }

    override fun save(tokens: AuthTokens) {
        file.parentFile?.mkdirs()
        val props = Properties().apply {
            setProperty(KEY_ACCESS_TOKEN, tokens.accessToken)
            setProperty(KEY_REFRESH_TOKEN, tokens.refreshToken)
        }
        file.outputStream().use { props.store(it, null) }
    }

    override fun clear() {
        file.delete()
    }

    private companion object {
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
    }
}
