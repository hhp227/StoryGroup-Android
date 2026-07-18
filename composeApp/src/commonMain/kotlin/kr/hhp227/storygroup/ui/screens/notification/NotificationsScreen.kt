package kr.hhp227.storygroup.ui.screens.notification

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kr.hhp227.storygroup.ui.components.SgEmptyState

/** 알림 — TODO: shared에 NotificationRepository/ViewModel 추가 후 목록 구현 */
@Composable
fun NotificationsScreen(modifier: Modifier = Modifier) {
    SgEmptyState(
        title = "알림이 없습니다",
        subtitle = "새 소식이 생기면 여기에 표시됩니다.",
        icon = Icons.Default.Notifications,
        modifier = modifier
    )
}
