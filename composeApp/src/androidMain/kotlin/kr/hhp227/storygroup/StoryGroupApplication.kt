package kr.hhp227.storygroup

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import kr.hhp227.storygroup.push.StoryGroupMessagingService
import kr.hhp227.storygroup.shared.di.AppContainer
import kr.hhp227.storygroup.shared.data.source.AndroidNetworkStatusDataSource
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
            imageCompressor = AndroidImageCompressor(this),
            networkStatusDataSource = AndroidNetworkStatusDataSource(this)
        )

        // 푸시 채널 — 서비스(StoryGroupMessagingService)가 kind별로 나눠 쓴다(API 26+, minSdk 24라 가드 필요)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(
                NotificationChannel(StoryGroupMessagingService.CHANNEL_CHAT, "채팅", NotificationManager.IMPORTANCE_HIGH)
            )
            notificationManager.createNotificationChannel(
                NotificationChannel(StoryGroupMessagingService.CHANNEL_NOTIFICATION, "알림", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }
}
