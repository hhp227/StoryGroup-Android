package kr.hhp227.storygroup.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import kr.hhp227.storygroup.ui.navigation.MainDestination
import kr.hhp227.storygroup.ui.navigation.NavigationAction
import kr.hhp227.storygroup.ui.screens.chat.ChatScreen
import kr.hhp227.storygroup.ui.screens.friend.FriendsScreen
import kr.hhp227.storygroup.ui.screens.group.GroupsScreen
import kr.hhp227.storygroup.ui.screens.home.HomeScreen
import kr.hhp227.storygroup.ui.screens.notification.NotificationsScreen
import kr.hhp227.storygroup.ui.screens.profile.ProfileScreen
import kr.hhp227.storygroup.ui.theme.NavStyle
import kr.hhp227.storygroup.ui.theme.ThemeState

/**
 * 내비게이션 쉘 — 설정>내비게이션 스타일에 따라 하단 탭/레거시 드로어를 교체한다.
 * 화면(콘텐츠)은 두 쉘이 완전 공유. 화면별 ViewModel은 각 화면이 default parameter로 선언한다.
 * 탭 상태(currentTab)는 NavigationViewModel이 소유한다 — 쉘은 더 이상 자체 rememberSaveable로
 * 들고 있지 않는다. 앱 설정도 쉘 내부 오버레이가 아니라 NavHost 목적지(Route.AppSettings)로
 * 승격됐다 — 그룹 상세 위에서도 같은 화면을 열 수 있어야 했기 때문.
 *
 * ⚠️ 알려진 트레이드오프: rememberSaveable은 프로세스 사망도 버텼지만 NavigationViewModel의
 * currentTab은 못 버틴다(SavedStateHandle을 안 쓰는 평범한 StateFlow). 그래서 프로세스가
 * 죽었다 복원되면 NavHost 백스택(예: 그룹 상세 화면)은 그대로 돌아오는데 그 아래 쉘의 탭은
 * 조용히 HOME으로 리셋돼 있을 수 있다. 제대로 고치려면 세션 ViewModelStoreOwner가
 * SavedStateRegistryOwner도 겸해야 하는데, 그건 공유 인프라 영역이라 이번엔 범위 밖으로 남겨둔다.
 */
@Composable
fun MainShell(
    themeState: ThemeState,
    currentTab: MainDestination,
    onNavigationAction: (NavigationAction) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (themeState.navStyle) {
        NavStyle.TABS -> TabShell(
            currentTab = currentTab,
            onNavigationAction = onNavigationAction,
            onLogout = onLogout,
            modifier = modifier
        )
        NavStyle.DRAWER -> DrawerShell(
            currentTab = currentTab,
            onNavigationAction = onNavigationAction,
            onLogout = onLogout,
            modifier = modifier
        )
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
    onLogout: () -> Unit,
    menuNavigationIcon: (@Composable () -> Unit)?
) {
    when (destination) {
        // 홈은 셸 상단바 없이 화면이 콜랩싱 헤더(레거시 라운지 CollapsingToolbar 미러)를 직접 그린다
        MainDestination.HOME -> HomeScreen(navigationIcon = menuNavigationIcon)
        // 그룹 탭도 화면이 상단바를 소유(홈과 동일) — 상세는 NavHost 풀스크린 목적지로 승격됨
        MainDestination.GROUPS -> GroupsScreen(navigationIcon = menuNavigationIcon)
        // 친구 탭 "메시지" 버튼 → DM 채팅방(groupId=null), 행 탭 → 공개 프로필(웹 /users/[id] 미러)
        MainDestination.FRIENDS -> FriendsScreen()
        MainDestination.CHAT -> ChatScreen()
        MainDestination.NOTIFICATIONS -> NotificationsScreen()
        MainDestination.PROFILE -> ProfileScreen(onLogout = onLogout)
    }
}
