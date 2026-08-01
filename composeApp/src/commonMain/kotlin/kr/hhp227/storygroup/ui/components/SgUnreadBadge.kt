package kr.hhp227.storygroup.ui.components

import androidx.compose.material.Badge
import androidx.compose.material.BadgedBox
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kr.hhp227.storygroup.ui.theme.SgTheme

/** 미읽음 수 뱃지 — 0이면 그리지 않고, 99 초과는 "99+"(레거시 BadgeDrawable 관행) */
@Composable
fun SgUnreadBadge(count: Long, modifier: Modifier = Modifier) {
    if (count <= 0) return

    Badge(
        modifier = modifier,
        backgroundColor = SgTheme.colors.accent,
        contentColor = SgTheme.colors.onAccent
    ) {
        Text(if (count > 99) "99+" else count.toString())
    }
}

/** 종 아이콘+미읽음 뱃지 상단바 액션 — 셸 2종·홈·그룹 상단바 공용(알림 세션 VM의 unreadCount를 받는다) */
@Composable
fun SgBellAction(unreadCount: Long, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        BadgedBox(badge = { SgUnreadBadge(unreadCount) }) {
            Icon(Icons.Default.Notifications, contentDescription = "알림")
        }
    }
}
