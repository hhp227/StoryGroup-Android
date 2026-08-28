package kr.hhp227.storygroup

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.push.StoryGroupMessagingService
import kr.hhp227.storygroup.shared.di.AppContainer
import kr.hhp227.storygroup.shared.data.storage.InMemoryTokenStorage
import kr.hhp227.storygroup.shared.domain.model.PushPlatform
import kr.hhp227.storygroup.ui.navigation.PushDeepLink

class MainActivity : ComponentActivity() {
    private val pendingDeepLink = mutableStateOf<PushDeepLink?>(null)
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 거부 시 재요청 없음 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 스플래시 테마(Theme.StoryGroup.Splash)와 짝 — super.onCreate 전에 설치해야 한다
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as StoryGroupApplication).container
        pendingDeepLink.value = intent?.extras?.toPushDeepLink()

        setContent {
            App(
                container = container,
                pendingDeepLink = pendingDeepLink.value,
                onConsumeDeepLink = { pendingDeepLink.value = null },
                onSessionStart = {
                    if (Build.VERSION.SDK_INT >= 33 &&
                        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                        lifecycleScope.launch {
                            runCatching { container.registerPushTokenUseCase(token, PushPlatform.ANDROID) }
                        }
                    }
                },
                onSessionEnd = {
                    FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                        // Activity 수명과 무관하게 발사 — 실패해도 로그아웃은 진행(설계 §10)
                        CoroutineScope(Dispatchers.IO).launch {
                            runCatching { container.unregisterPushTokenUseCase(token) }
                        }
                    }
                }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingDeepLink.value = intent.extras?.toPushDeepLink()
    }
}

// 인텐트 extras(StoryGroupMessagingService의 EXTRA_*) → PushDeepLink
private fun Bundle.toPushDeepLink(): PushDeepLink? {
    val kind = getString(StoryGroupMessagingService.EXTRA_KIND) ?: return null
    return PushDeepLink(
        kind = kind,
        chatRoomId = if (containsKey(StoryGroupMessagingService.EXTRA_CHAT_ROOM_ID)) getLong(StoryGroupMessagingService.EXTRA_CHAT_ROOM_ID) else null,
        groupId = if (containsKey(StoryGroupMessagingService.EXTRA_GROUP_ID)) getLong(StoryGroupMessagingService.EXTRA_GROUP_ID) else null,
        postId = if (containsKey(StoryGroupMessagingService.EXTRA_POST_ID)) getLong(StoryGroupMessagingService.EXTRA_POST_ID) else null,
        roomTitle = getString(StoryGroupMessagingService.EXTRA_ROOM_TITLE)
    )
}

@Preview
@Composable
fun AppAndroidPreview() {
    App(AppContainer(InMemoryTokenStorage()))
}
