package kr.hhp227.storygroup

import android.app.Application
import kr.hhp227.storygroup.di.AppContainer
import kr.hhp227.storygroup.shared.data.storage.SharedPreferencesTokenStorage
import kr.hhp227.storygroup.shared.data.storage.SharedPreferencesKeyValueStorage
import kr.hhp227.storygroup.ui.util.AndroidImageCompressor

/** 프로세스 단위 DI 컨테이너 보유 — Activity 재생성과 무관하게 HttpClient를 하나만 유지한다 */
class StoryGroupApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(
            tokenStorage = SharedPreferencesTokenStorage(this),
            settingsStorage = SharedPreferencesKeyValueStorage(this),
            imageCompressor = AndroidImageCompressor(this)
        )
    }
}
