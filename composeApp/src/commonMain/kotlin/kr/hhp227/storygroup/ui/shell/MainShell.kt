package kr.hhp227.storygroup.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.material.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import kr.hhp227.storygroup.shared.domain.model.Group
import kr.hhp227.storygroup.ui.screens.chat.ChatScreen
import kr.hhp227.storygroup.ui.screens.friend.FriendsScreen
import kr.hhp227.storygroup.ui.screens.group.GroupsScreen
import kr.hhp227.storygroup.ui.screens.home.HomeScreen
import kr.hhp227.storygroup.ui.screens.notification.NotificationsScreen
import kr.hhp227.storygroup.ui.screens.profile.ProfileScreen
import kr.hhp227.storygroup.ui.screens.settings.AppSettingsScreen
import kr.hhp227.storygroup.ui.theme.NavStyle
import kr.hhp227.storygroup.ui.theme.SgTheme
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
 * 화면(콘텐츠)은 두 쉘이 완전 공유. 화면별 ViewModel은 각 화면이 default parameter로 선언한다.
 */
@Composable
fun MainShell(
    themeState: ThemeState,
    onOpenGroupDetail: (Group) -> Unit,
    onCreatePost: () -> Unit,
    // 채팅방 풀스크린 목적지(그룹 상세와 동일한 NavHost 배선) — 그룹 방은 groupId, DM은 null
    onOpenChatRoom: (chatRoomId: Long, groupId: Long?, title: String) -> Unit,
    // 홈(라운지) 글쓰기 성공 신호 — HomeScreen이 소비하고 onHomeRefreshHandled로 소거한다
    homeRefreshRequested: Boolean,
    onHomeRefreshHandled: () -> Unit,
    // 계정 설정/그룹 만들기/그룹 찾기는 NavHost 풀스크린 목적지(앱 설정 오버레이와 달리 App이 배선)
    onOpenAccountSettings: () -> Unit,
    onOpenCreateGroup: () -> Unit,
    onOpenDiscoverGroups: () -> Unit,
    onLogout: () -> Unit
) {
    var currentDestination by rememberSaveable { mutableStateOf(MainDestination.HOME) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    // 설정은 쉘을 대체하지 않고 위에 얹는다 — 쉘(탭 상태·스크롤)이 설정을 다녀와도 유지되도록
    Box {
        when (themeState.navStyle) {
            NavStyle.TABS -> TabShell(
                currentDestination = currentDestination,
                onDestinationSelected = { currentDestination = it },
                onOpenGroupDetail = onOpenGroupDetail,
                onCreatePost = onCreatePost,
                onOpenChatRoom = onOpenChatRoom,
                homeRefreshRequested = homeRefreshRequested,
                onHomeRefreshHandled = onHomeRefreshHandled,
                onOpenSettings = { showSettings = true },
                onOpenAccountSettings = onOpenAccountSettings,
                onOpenCreateGroup = onOpenCreateGroup,
                onOpenDiscoverGroups = onOpenDiscoverGroups,
                onLogout = onLogout
            )
            NavStyle.DRAWER -> DrawerShell(
                currentDestination = currentDestination,
                onDestinationSelected = { currentDestination = it },
                onOpenGroupDetail = onOpenGroupDetail,
                onCreatePost = onCreatePost,
                onOpenChatRoom = onOpenChatRoom,
                homeRefreshRequested = homeRefreshRequested,
                onHomeRefreshHandled = onHomeRefreshHandled,
                onOpenSettings = { showSettings = true },
                onOpenAccountSettings = onOpenAccountSettings,
                onOpenCreateGroup = onOpenCreateGroup,
                onOpenDiscoverGroups = onOpenDiscoverGroups,
                onLogout = onLogout
            )
        }
        if (showSettings) {
            // Surface가 아래 쉘로의 터치 전파를 막는다
            Surface(color = SgTheme.colors.paper) {
                AppSettingsScreen(themeState = themeState, onBack = { showSettings = false })
            }
        }
    }
}

/**
 * 두 쉘이 공유하는 목적지 → 화면 매핑 — iOS 셸의 keep-alive ZStack 미러.
 * 전 목적지를 컴포지션에 유지하고 현재 것만 측정/배치한다 — dispose가 없으므로 스크롤 위치와
 * Paging 프레젠터가 탭 전환·풀스크린 목적지(NavHost 오버레이) 왕복에도 그대로 살아있다.
 * (숨은 목적지는 측정/그리기/히트테스트 비용이 없다)
 */
@Composable
internal fun DestinationContent(
    destination: MainDestination,
    onOpenGroupDetail: (Group) -> Unit,
    onCreatePost: () -> Unit,
    onOpenChatRoom: (chatRoomId: Long, groupId: Long?, title: String) -> Unit,
    homeRefreshRequested: Boolean,
    onHomeRefreshHandled: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccountSettings: () -> Unit,
    onOpenCreateGroup: () -> Unit,
    onOpenDiscoverGroups: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    // 드로어 쉘이 화면 소유 상단바(홈·그룹)에 얹는 메뉴 아이콘(탭 쉘은 없음)
    menuNavigationIcon: (@Composable () -> Unit)? = null
) {
    Layout(
        content = {
            MainDestination.entries.forEach { dest ->
                key(dest) {
                    Box {
                        DestinationScreen(
                            destination = dest,
                            onOpenGroupDetail = onOpenGroupDetail,
                            onCreatePost = onCreatePost,
                            onOpenChatRoom = onOpenChatRoom,
                            homeRefreshRequested = homeRefreshRequested,
                            onHomeRefreshHandled = onHomeRefreshHandled,
                            onOpenNotifications = onOpenNotifications,
                            onOpenSettings = onOpenSettings,
                            onOpenAccountSettings = onOpenAccountSettings,
                            onOpenCreateGroup = onOpenCreateGroup,
                            onOpenDiscoverGroups = onOpenDiscoverGroups,
                            onLogout = onLogout,
                            menuNavigationIcon = menuNavigationIcon
                        )
                    }
                }
            }
        },
        modifier = modifier
    ) { measurables, constraints ->
        // 현재 목적지만 측정/배치 — 나머지는 컴포지션(상태)만 유지된다
        val placeable = measurables[MainDestination.entries.indexOf(destination)].measure(constraints)

        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.place(0, 0)
        }
    }
}

@Composable
private fun DestinationScreen(
    destination: MainDestination,
    onOpenGroupDetail: (Group) -> Unit,
    onCreatePost: () -> Unit,
    onOpenChatRoom: (chatRoomId: Long, groupId: Long?, title: String) -> Unit,
    homeRefreshRequested: Boolean,
    onHomeRefreshHandled: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccountSettings: () -> Unit,
    onOpenCreateGroup: () -> Unit,
    onOpenDiscoverGroups: () -> Unit,
    onLogout: () -> Unit,
    menuNavigationIcon: (@Composable () -> Unit)?
) {
    when (destination) {
        // 홈은 셸 상단바 없이 화면이 콜랩싱 헤더(레거시 라운지 CollapsingToolbar 미러)를 직접 그린다
        MainDestination.HOME -> HomeScreen(
            onCreatePost = onCreatePost,
            refreshRequested = homeRefreshRequested,
            onRefreshHandled = onHomeRefreshHandled,
            onOpenNotifications = onOpenNotifications,
            navigationIcon = menuNavigationIcon
        )
        // 그룹 탭도 화면이 상단바를 소유(홈과 동일) — 상세는 NavHost 풀스크린 목적지로 승격됨
        MainDestination.GROUPS -> GroupsScreen(
            onOpenGroup = onOpenGroupDetail,
            onOpenNotifications = onOpenNotifications,
            onOpenCreateGroup = onOpenCreateGroup,
            onOpenDiscoverGroups = onOpenDiscoverGroups,
            navigationIcon = menuNavigationIcon
        )
        MainDestination.FRIENDS -> FriendsScreen()
        MainDestination.CHAT -> ChatScreen(onOpenChatRoom = onOpenChatRoom)
        MainDestination.NOTIFICATIONS -> NotificationsScreen()
        MainDestination.PROFILE -> ProfileScreen(
            onOpenAccountSettings = onOpenAccountSettings,
            onOpenSettings = onOpenSettings,
            onLogout = onLogout
        )
    }
}
