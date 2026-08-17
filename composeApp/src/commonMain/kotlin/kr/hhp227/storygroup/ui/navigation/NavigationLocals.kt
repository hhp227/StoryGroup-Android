package kr.hhp227.storygroup.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import androidx.navigation.NavDestination.Companion.hasRoute
import kr.hhp227.storygroup.di.sessionViewModel

/**
 * 세션 스코프의 단일 NavigationViewModel — 화면들이 default parameter로 이걸 조회해
 * onNavigationAction을 얻는다(호출부가 람다를 내려줄 필요가 없다).
 */
@Composable
fun sessionNavigationViewModel(): NavigationViewModel = sessionViewModel { NavigationViewModel() }

/**
 * 현재 최상단 목적지 — 셸만 보이는 상태(Route.Shell)면 null.
 * NavBackStackEntry.toRoute<T>()는 타입을 알아야 하므로 hasRoute로 판별해 분기한다.
 * ⚠️ "최상단"의 정직한 의미 그대로다: `dialog<>` 목적지가 떠 있으면 그 다이얼로그 자체를
 * 돌려준다(예: Route.UserProfile). 2-pane처럼 "다이얼로그 아래" 화면이 필요한 호출부는
 * [currentPaneRouteAsState]를 대신 써라 — 안 그러면 프로필 카드를 열 때마다 그 판정이 무너진다.
 */
@Composable
fun NavHostController.currentTopRouteAsState(): Route? {
    val entry by currentBackStackEntryAsState()

    return entry?.toRouteOrNull()
}

/**
 * 2-pane 판정용 목적지 — 다이얼로그는 뒤 화면 위에 뜨는 카드라 아래 레이아웃을 바꾸면 안 된다.
 * 최상단이 다이얼로그면 그 아래 목적지로 판정한다(그렇지 않으면 프로필 카드를 열 때마다 2-pane이 무너진다:
 * 그룹 상세에서 멤버 아바타를 눌러 프로필을 열면 최상단이 Route.UserProfile → DIALOG → twoPane=false로
 * 떨어져 Row가 Box로 바뀌고 그룹 상세가 스크림 뒤 풀스크린으로 튄다).
 */
@Composable
fun NavHostController.currentPaneRouteAsState(): Route? {
    val top = currentTopRouteAsState()

    return if (top != null && paneModeFor(top) == PaneMode.DIALOG) {
        previousBackStackEntry?.toRouteOrNull()
    } else {
        top
    }
}

private fun NavBackStackEntry.toRouteOrNull(): Route? = when {
    destination.hasRoute<Route.GroupDetail>() -> toRoute<Route.GroupDetail>()
    destination.hasRoute<Route.PostDetail>() -> toRoute<Route.PostDetail>()
    destination.hasRoute<Route.ChatRoom>() -> toRoute<Route.ChatRoom>()
    destination.hasRoute<Route.UserProfile>() -> toRoute<Route.UserProfile>()
    destination.hasRoute<Route.CreatePost>() -> toRoute<Route.CreatePost>()
    destination.hasRoute<Route.GroupEdit>() -> toRoute<Route.GroupEdit>()
    destination.hasRoute<Route.GroupReports>() -> toRoute<Route.GroupReports>()
    destination.hasRoute<Route.Call>() -> toRoute<Route.Call>()
    destination.hasRoute<Route.AccountSettings>() -> Route.AccountSettings
    destination.hasRoute<Route.AppSettings>() -> Route.AppSettings
    destination.hasRoute<Route.BlockedUsers>() -> Route.BlockedUsers
    destination.hasRoute<Route.CreateGroup>() -> Route.CreateGroup
    destination.hasRoute<Route.DiscoverGroups>() -> Route.DiscoverGroups
    destination.hasRoute<Route.Search>() -> Route.Search
    // Route.Shell = 셸만 보이는 상태 — 오버레이 없음
    else -> null
}
