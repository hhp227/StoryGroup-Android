package kr.hhp227.storygroup.push

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.MainActivity
import kr.hhp227.storygroup.R
import kr.hhp227.storygroup.StoryGroupApplication
import kr.hhp227.storygroup.shared.domain.model.PushPlatform
import kr.hhp227.storygroup.ui.navigation.PushDeepLink

/**
 * data-only FCM 수신(서버는 top-level notification을 안 실음 — 설계 §4).
 * 앱이 포그라운드면 표시하지 않는다 — 인앱 STOMP가 이미 뱃지·실시간을 담당.
 */
class StoryGroupMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // 미로그인 상태면 401로 실패 — 무해(로그인 시 onSessionStart가 재등록)
        val container = (application as StoryGroupApplication).container
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { container.registerPushTokenUseCase(token, PushPlatform.ANDROID) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        val data = message.data
        val title = data["title"] ?: return
        val link = PushDeepLink.fromData(data)
        val isChat = link.kind == "CHAT"

        // 채팅은 방 단위로 접기(같은 방 새 메시지가 알림 1개를 갱신), 알림 피드는 건별
        val notificationId = if (isChat) (link.chatRoomId ?: 0L).toInt() else (data["notificationId"]?.toIntOrNull() ?: 0)
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            // kind+id로 action을 갈라 PendingIntent가 kind 간·건 간 공유되지 않게 한다(filterEquals는 extras를 안 본다)
            action = link.kind + notificationId
            putExtra(EXTRA_KIND, link.kind)
            link.chatRoomId?.let { putExtra(EXTRA_CHAT_ROOM_ID, it) }
            link.groupId?.let { putExtra(EXTRA_GROUP_ID, it) }
            link.postId?.let { putExtra(EXTRA_POST_ID, it) }
            link.roomTitle?.let { putExtra(EXTRA_ROOM_TITLE, it) }
        }
        val pendingIntent = PendingIntent.getActivity(
            this, notificationId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, if (isChat) CHANNEL_CHAT else CHANNEL_NOTIFICATION)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(data["body"] ?: "")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        getSystemService(NotificationManager::class.java).notify(link.kind + notificationId, notificationId, notification)
    }

    companion object {
        const val CHANNEL_CHAT = "chat"
        const val CHANNEL_NOTIFICATION = "notification"
        const val EXTRA_KIND = "push_kind"
        const val EXTRA_CHAT_ROOM_ID = "push_chat_room_id"
        const val EXTRA_GROUP_ID = "push_group_id"
        const val EXTRA_POST_ID = "push_post_id"
        const val EXTRA_ROOM_TITLE = "push_room_title"
    }
}
