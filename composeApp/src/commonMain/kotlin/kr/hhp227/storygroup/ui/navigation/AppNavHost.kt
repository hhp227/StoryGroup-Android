package kr.hhp227.storygroup.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.toRoute
import kr.hhp227.storygroup.ui.screens.chat.ChatRoomScreen
import kr.hhp227.storygroup.ui.screens.call.CallScreen
import kr.hhp227.storygroup.ui.screens.group.CreateGroupScreen
import kr.hhp227.storygroup.ui.screens.group.DiscoverGroupsScreen
import kr.hhp227.storygroup.ui.screens.group.GroupDetailScreen
import kr.hhp227.storygroup.ui.screens.group.GroupEditScreen
import kr.hhp227.storygroup.ui.screens.group.GroupReportsScreen
import kr.hhp227.storygroup.ui.screens.post.CreatePostScreen
import kr.hhp227.storygroup.ui.screens.post.PostDetailScreen
import kr.hhp227.storygroup.ui.screens.search.SearchScreen
import kr.hhp227.storygroup.ui.screens.settings.AccountSettingsScreen
import kr.hhp227.storygroup.ui.screens.settings.AppSettingsScreen
import kr.hhp227.storygroup.ui.screens.settings.BlockedUsersScreen
import kr.hhp227.storygroup.ui.screens.user.UserProfileScreen
import kr.hhp227.storygroup.ui.theme.SgTheme
import kr.hhp227.storygroup.ui.theme.ThemeState

/**
 * 셸 위를 덮는(또는 2-pane에서 우측에 서는) 목적지들.
 * 셸 자체는 이 NavHost 밖에서 항상 살아있다 — 시작 목적지가 빈 Route.Shell인 이유다.
 *
 * 아래 목적지 대부분이 `Modifier.windowInsetsPadding(WindowInsets.navigationBars)`를 붙이는 이유:
 * 풀스크린이라 셸의 bottomBar가 없다 — 하단 시스템 내비바 인셋을 화면이 직접 소화해야 한다.
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    themeState: ThemeState,
    modifier: Modifier = Modifier
) {
    NavHost(navController = navController, startDestination = Route.Shell, modifier = modifier) {
        composable<Route.Shell> {}
        composable<Route.GroupDetail> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.GroupDetail>()

            DestinationSurface {
                GroupDetailScreen(
                    groupId = route.groupId,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                )
            }
        }
        composable<Route.ChatRoom> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.ChatRoom>()

            DestinationSurface {
                ChatRoomScreen(
                    chatRoomId = route.chatRoomId,
                    groupId = route.groupId,
                    title = route.title,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                )
            }
        }
        composable<Route.PostDetail> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.PostDetail>()

            DestinationSurface {
                PostDetailScreen(
                    groupId = route.groupId,
                    postId = route.postId,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                )
            }
        }
        composable<Route.CreatePost> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.CreatePost>()

            DestinationSurface {
                CreatePostScreen(
                    groupId = route.groupId,
                    postId = route.postId,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                )
            }
        }
        composable<Route.Call> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.Call>()

            DestinationSurface {
                CallScreen(
                    chatRoomId = route.chatRoomId,
                    title = route.title,
                    ring = route.ring,
                    video = route.video,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                )
            }
        }
        composable<Route.GroupEdit> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.GroupEdit>()

            DestinationSurface {
                GroupEditScreen(
                    groupId = route.groupId,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                )
            }
        }
        composable<Route.GroupReports> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.GroupReports>()

            DestinationSurface {
                GroupReportsScreen(
                    groupId = route.groupId,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                )
            }
        }
        composable<Route.AccountSettings> {
            DestinationSurface {
                AccountSettingsScreen(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars))
            }
        }
        composable<Route.BlockedUsers> {
            DestinationSurface {
                BlockedUsersScreen(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars))
            }
        }
        composable<Route.AppSettings> {
            // AppSettingsScreen은 이번 전환 범위 밖(onBack을 그대로 받는다) — 여기서 세션 VM을
            // 직접 조회해 다른 화면들과 같은 NavigateBack 경로로 이어준다
            val onNavigationAction = sessionNavigationViewModel()::onAction

            DestinationSurface {
                AppSettingsScreen(
                    themeState = themeState,
                    onBack = { onNavigationAction(NavigationAction.NavigateBack) }
                )
            }
        }
        composable<Route.CreateGroup> {
            DestinationSurface {
                CreateGroupScreen(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars))
            }
        }
        composable<Route.DiscoverGroups> {
            DestinationSurface {
                DiscoverGroupsScreen(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars))
            }
        }
        composable<Route.Search> {
            DestinationSurface {
                SearchScreen(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars))
            }
        }

        // 풀스크린이 아니라 카드 다이얼로그 — 뒤 화면이 스크림 너머로 남는다(iOS 시트 미러).
        // DM·프로필 수정으로 navigate하면 Navigation이 다이얼로그를 자동 pop하고 목적지를 연다
        dialog<Route.UserProfile>(
            dialogProperties = DialogProperties(usePlatformDefaultWidth = false)
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<Route.UserProfile>()
            // 바로 아래가 채팅방이면 그 방 id — 화면이 같은 방 가드 판정에 쓴다
            val underlyingChatRoomId = navController.previousBackStackEntry
                ?.takeIf { it.destination.hasRoute<Route.ChatRoom>() }
                ?.toRoute<Route.ChatRoom>()
                ?.chatRoomId

            UserProfileScreen(
                userId = route.userId,
                underlyingChatRoomId = underlyingChatRoomId,
                // usePlatformDefaultWidth=false라 폭은 화면이 직접 잡는다(폰=92%, 데스크탑=최대 420dp)
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(0.92f)
            )
        }
    }
}

/**
 * 목적지 콘텐츠를 불투명 배경(paper)으로 감싼다 — 좁은 창에선 아래 셸로의 터치 전파를 막고,
 * 2-pane 우측 패널에선 배경을 칠한다. 예전엔 좁은 창(오버레이)에서만 감쌌지만, 그건 Surface가
 * 터치를 막는 효과만 본 것이고 배경을 칠하는 효과를 놓친 판단이었다 — GroupDetailScreen처럼
 * 스스로 `.background()`를 안 칠하는 화면은 2-pane 우측 패널에서 다크모드 시 흰 판이 됐다.
 * 두 효과 다 자기 영역 안에서만 작동해 나란히 둬도 무해하니 항상 감싼다.
 * contentColorFor(paper)는 Unspecified라 LocalContentColor로 폴백돼 색 변화는 없다.
 */
@Composable
private fun DestinationSurface(content: @Composable () -> Unit) {
    Surface(color = SgTheme.colors.paper) { content() }
}
