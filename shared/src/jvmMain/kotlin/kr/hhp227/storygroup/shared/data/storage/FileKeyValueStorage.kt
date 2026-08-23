package kr.hhp227.storygroup.shared.data.storage

import java.io.File
import java.util.Properties
import kr.hhp227.storygroup.shared.domain.storage.KeyValueStorage

/** Desktop(JVM)용 설정 저장소 — FileTokenStorage와 같은 위치(~/.storygroup)에 보관 */
class FileKeyValueStorage(
    private val file: File = File(System.getProperty("user.home"), ".storygroup/settings.properties")
) : KeyValueStorage {

    override fun getString(key: String): String? = load().getProperty(key)

    override fun putString(key: String, value: String) {
        store(load().apply { setProperty(key, value) })
    }

    override fun remove(key: String) {
        store(load().apply { remove(key) })
    }

    private fun load(): Properties = Properties().apply {
        if (file.exists()) file.inputStream().use(::load)
    }

    private fun store(props: Properties) {
        file.parentFile?.mkdirs()
        file.outputStream().use { props.store(it, null) }
    }
}
