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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import kr.hhp227.storygroup.shared.domain.model.Group
import kr.hhp227.storygroup.ui.screens.chat.ChatScreen
import kr.hhp227.storygroup.ui.screens.friend.FriendsScreen
import kr.hhp227.storygroup.ui.screens.group.GroupsScreen
import kr.hhp227.storygroup.ui.screens.group.GroupsViewModel
import kr.hhp227.storygroup.ui.screens.home.HomeScreen
import kr.hhp227.storygroup.ui.screens.home.HomeViewModel
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
fun MainShell(
    themeState: ThemeState,
    profile: Profile?,
    homeViewModel: HomeViewModel,
    groupsViewModel: GroupsViewModel,
    onOpenGroupDetail: (Group) -> Unit,
    onLogout: () -> Unit
) {
    // 상세(NavHost 목적지)로 나갔다 돌아와도 탭 선택이 유지되게 saveable로 승격
    var currentDestination by rememberSaveable { mutableStateOf(MainDestination.HOME) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    if (showSettings) {
        AppSettingsScreen(themeState = themeState, onBack = { showSettings = false })
        return
    }
    when (themeState.navStyle) {
        NavStyle.TABS -> TabShell(
            currentDestination = currentDestination,
            onDestinationSelected = { currentDestination = it },
            profile = profile,
            homeViewModel = homeViewModel,
            groupsViewModel = groupsViewModel,
            onOpenGroupDetail = onOpenGroupDetail,
            onOpenSettings = { showSettings = true },
            onLogout = onLogout
        )
        NavStyle.DRAWER -> DrawerShell(
            currentDestination = currentDestination,
            onDestinationSelected = { currentDestination = it },
            profile = profile,
            homeViewModel = homeViewModel,
            groupsViewModel = groupsViewModel,
            onOpenGroupDetail = onOpenGroupDetail,
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
    homeViewModel: HomeViewModel,
    groupsViewModel: GroupsViewModel,
    onOpenGroupDetail: (Group) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    // 드로어 쉘이 화면 소유 상단바(홈·그룹)에 얹는 메뉴 아이콘(탭 쉘은 없음)
    menuNavigationIcon: (@Composable () -> Unit)? = null
) {
    // enum 전환이라 목적지를 떠나면 컴포저블이 dispose됨 — 스크롤 위치(rememberLazyListState 등
    // rememberSaveable 기반 상태)가 초기화되지 않게 목적지별로 보존/복원한다.
    // 풀스크린 목적지(그룹 상세)는 App의 NavHost가 담당하고, 탭 전환은 여기 enum이 담당한다.
    val stateHolder = rememberSaveableStateHolder()

    stateHolder.SaveableStateProvider(destination.name) {
        DestinationScreen(
            destination = destination,
            profile = profile,
            homeViewModel = homeViewModel,
            groupsViewModel = groupsViewModel,
            onOpenGroupDetail = onOpenGroupDetail,
            onOpenNotifications = onOpenNotifications,
            onOpenSettings = onOpenSettings,
            onLogout = onLogout,
            menuNavigationIcon = menuNavigationIcon,
            modifier = modifier
        )
    }
}

@Composable
private fun DestinationScreen(
    destination: MainDestination,
    profile: Profile?,
    homeViewModel: HomeViewModel,
    groupsViewModel: GroupsViewModel,
    onOpenGroupDetail: (Group) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit,
    menuNavigationIcon: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier
) {
    when (destination) {
        // 홈은 셸 상단바 없이 화면이 콜랩싱 헤더(레거시 라운지 CollapsingToolbar 미러)를 직접 그린다
        MainDestination.HOME -> HomeScreen(
            viewModel = homeViewModel,
            onOpenNotifications = onOpenNotifications,
            navigationIcon = menuNavigationIcon,
            modifier = modifier
        )
        // 그룹 탭도 화면이 상단바를 소유(홈과 동일) — 상세는 NavHost 풀스크린 목적지로 승격됨
        MainDestination.GROUPS -> GroupsScreen(
            viewModel = groupsViewModel,
            onOpenGroup = onOpenGroupDetail,
            onOpenNotifications = onOpenNotifications,
            navigationIcon = menuNavigationIcon,
            modifier = modifier
        )
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
