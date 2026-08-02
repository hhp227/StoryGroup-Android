package kr.hhp227.storygroup.ui.rtc

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * 화면 공유용 포그라운드 서비스 — API 29+는 mediaProjection 타입 FGS가 떠 있어야
 * getMediaProjection이 허용된다(웹에는 없는 Android 제약). 캡처 자체는 세션이 하고,
 * 이 서비스는 알림 하나로 "공유 중" 상태만 유지한다. 공유 중지·세션 폐기 때 stop으로 내린다.
 */
internal class ScreenShareService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "화면 공유", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentTitle("화면 공유 중")
            .setOngoing(true)
            .build()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        )
        // FGS가 뜬 다음에야 getMediaProjection이 안전하다 — 대기 중인 캡처 시작을 이제 진행시킨다
        onStarted?.invoke()
        onStarted = null
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL_ID = "screen_share"
        private const val NOTIFICATION_ID = 2001
        private var onStarted: (() -> Unit)? = null

        fun start(context: Context, onReady: () -> Unit) {
            onStarted = onReady
            ContextCompat.startForegroundService(context, Intent(context, ScreenShareService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ScreenShareService::class.java))
        }
    }
}
