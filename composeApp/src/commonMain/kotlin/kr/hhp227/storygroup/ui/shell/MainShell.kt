package kr.hhp227.storygroup.ui.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import kr.hhp227.storygroup.ui.screens.chat.ChatScreen
import kr.hhp227.storygroup.ui.screens.friend.FriendsScreen
import kr.hhp227.storygroup.ui.screens.group.GroupsScreen
import kr.hhp227.storygroup.ui.screens.home.HomeScreen
import kr.hhp227.storygroup.ui.screens.notification.NotificationsScreen
import kr.hhp227.storygroup.ui.screens.profile.ProfileScreen
import kr.hhp227.storygroup.shared.domain.model.Profile
import kr.hhp227.storygroup.ui.screens.settings.AppSettingsScreen
import kr.hhp227.storygroup.ui.theme.NavStyle
import kr.hhp227.storygroup.ui.theme.ThemeState

/** 내비 목적지 — 탭/레일은 5개(홈·그룹·친구·채팅·프로필), 알림은 상단바 종 아이콘으로 진입(레거시 드로어는 전부 노출) */
enum class MainDestination(val label: String, val icon: ImageVector, val inTabs: Boolean) {
    HOME("홈", Icons.Default.Home, true),
    GROUPS("그룹", Icons.Default.Groups, true),
    FRIENDS("친구", Icons.Default.People, true),
    CHAT("채팅", Icons.AutoMirrored.Filled.Chat, true),
    NOTIFICATIONS("알림", Icons.Default.Notifications, false),
    PROFILE("프로필", Icons.Default.Person, true)
}

/**
 * 내비게이션 쉘 — 설정>내비게이션 스타일에 따라 하단 탭/레거시 드로어를 교체한다.
 * 화면(콘텐츠)은 두 쉘이 완전 공유. 설정은 쉘 위를 덮는 전체 화면.
 */
@Composable
fun MainShell(themeState: ThemeState, profile: Profile?, onLogout: () -> Unit) {
    var currentDestination by remember { mutableStateOf(MainDestination.HOME) }
    var showSettings by remember { mutableStateOf(false) }

    if (showSettings) {
        AppSettingsScreen(themeState = themeState, onBack = { showSettings = false })
        return
    }
    when (themeState.navStyle) {
        NavStyle.TABS -> TabShell(
            currentDestination = currentDestination,
            onDestinationSelected = { currentDestination = it },
            profile = profile,
            onOpenSettings = { showSettings = true },
            onLogout = onLogout
        )
        NavStyle.DRAWER -> DrawerShell(
            currentDestination = currentDestination,
            onDestinationSelected = { currentDestination = it },
            profile = profile,
            onOpenSettings = { showSettings = true },
            onLogout = onLogout
        )
    }
}

/** 두 쉘이 공유하는 목적지 → 화면 매핑 */
@Composable
internal fun DestinationContent(
    destination: MainDestination,
    profile: Profile?,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (destination) {
        MainDestination.HOME -> HomeScreen(modifier)
        MainDestination.GROUPS -> GroupsScreen(modifier)
        MainDestination.FRIENDS -> FriendsScreen(modifier)
        MainDestination.CHAT -> ChatScreen(modifier)
        MainDestination.NOTIFICATIONS -> NotificationsScreen(modifier)
        MainDestination.PROFILE -> ProfileScreen(
            profile = profile,
            onOpenSettings = onOpenSettings,
            onLogout = onLogout,
            modifier = modifier
        )
    }
}
