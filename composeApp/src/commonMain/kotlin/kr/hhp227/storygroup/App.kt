package kr.hhp227.storygroup

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import kr.hhp227.storygroup.di.AppContainer
import kr.hhp227.storygroup.di.LocalAppContainer
import kr.hhp227.storygroup.di.LocalSessionViewModelStoreOwner
import kr.hhp227.storygroup.ui.screens.auth.LoginScreen
import kr.hhp227.storygroup.ui.screens.auth.LoginViewModel
import kr.hhp227.storygroup.ui.screens.auth.RegisterScreen
import kr.hhp227.storygroup.ui.screens.group.CreateGroupScreen
import kr.hhp227.storygroup.ui.screens.group.DiscoverGroupsScreen
import kr.hhp227.storygroup.ui.screens.group.GroupDetailScreen
import kr.hhp227.storygroup.ui.screens.post.CreatePostScreen
import kr.hhp227.storygroup.ui.screens.settings.AccountSettingsScreen
import kr.hhp227.storygroup.ui.shell.MainShell
import kr.hhp227.storygroup.ui.theme.NightMode
import kr.hhp227.storygroup.ui.theme.SgTheme
import kr.hhp227.storygroup.ui.theme.StoryGroupTheme
import kr.hhp227.storygroup.ui.theme.ThemeState

// 라우트는 internal — private이면 JVM(Desktop)에서 kotlinx.serialization 리플렉션이
// 패키지 전용 클래스에 접근하지 못해 시작 즉시 IllegalAccessException으로 죽는다(Android는 통과)
/**
 * NavHost 시작 목적지 — 아무것도 그리지 않는 빈 오버레이. 셸(MainShell)은 NavHost "밖"에서
 * 항상 컴포지션을 유지하고(iOS 루트 NavigationStack이 push 중에도 루트 뷰를 유지하는 것의 미러),
 * 풀스크린 목적지(그룹 상세/글쓰기)만 셸 위를 덮는다.
 */
@Serializable
internal data object ShellRoute

@Serializable
internal data class GroupDetailRoute(val groupId: Long)

/** 게시글 작성 — groupId null이면 라운지(홈 피드)에 게시(웹 메인 피드 폼 미러) */
@Serializable
internal data class CreatePostRoute(val groupId: Long?)

/** 계정 설정 — 프로필 수정+비밀번호 변경(웹 /settings/profile·password 미러) */
@Serializable
internal data object AccountSettingsRoute

/** 그룹 만들기 — 이름/소개/커버 이미지+가입 방식 */
@Serializable
internal data object CreateGroupRoute

/** 그룹 찾기 — 검색+정렬, 카드 탭 시 상세 다이얼로그에서 가입/신청(웹 그룹 찾기 탭 미러) */
@Serializable
internal data object DiscoverGroupsRoute

/** 그룹 피드 작성 성공을 이전 백스택 엔트리(그룹 상세)로 알리는 결과 키 — Paging-CRUD 샘플 미러 */
internal const val POST_CREATED_KEY = "post_created"

/** 루트 — 테마 적용 후 세션 상태(LoginViewModel)에 따라 인증 플로우/메인 쉘을 라우팅한다 */
@Composable
fun App(container: AppContainer) {
    val themeState = remember { ThemeState(container.settingsStorage) }
    val darkTheme = when (themeState.nightMode) {
        NightMode.SYSTEM -> isSystemInDarkTheme()
        NightMode.LIGHT -> false
        NightMode.DARK -> true
    }

    StoryGroupTheme(mood = themeState.mood, darkTheme = darkTheme) {
        CompositionLocalProvider(LocalAppContainer provides container) {
            // 세션 게이트 — 화면(LoginScreen)도 default parameter로 같은 인스턴스를 선언해 쓴다
            val loginViewModel = viewModel {
                LoginViewModel(container.isLoggedInUseCase, container.loginUseCase, container.logoutUseCase)
            }
            val loginUiState by loginViewModel.uiState.collectAsState()

            if (loginUiState.isLoggedIn) {
                SessionContent(
                    themeState = themeState,
                    onLogout = { loginViewModel.onAction(LoginViewModel.Action.Logout) }
                )
            } else {
                AuthFlow()
            }
        }
    }
}

/**
 * 로그인 세션 서브트리 — 세션 스코프 ViewModelStore를 제공하고, 로그아웃(dispose) 시 일괄 clear.
 * VM 선언은 각 화면의 default parameter 몫(ConCafe 패턴) — 여기엔 내비게이션 배선만 남는다.
 */
@Composable
private fun SessionContent(themeState: ThemeState, onLogout: () -> Unit) {
    val sessionOwner = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }

    DisposableEffect(sessionOwner) {
        onDispose { sessionOwner.viewModelStore.clear() }
    }
    CompositionLocalProvider(LocalSessionViewModelStoreOwner provides sessionOwner) {
        val navController = rememberNavController()
        // 홈(라운지) 작성 성공 신호 — 셸이 항상 살아있으므로 상태로 내려보낸다(그룹은 savedStateHandle)
        var homeRefreshPending by remember { mutableStateOf(false) }

        Box {
            MainShell(
                themeState = themeState,
                onOpenGroupDetail = { group -> navController.navigate(GroupDetailRoute(group.id)) },
                onCreatePost = { navController.navigate(CreatePostRoute(groupId = null)) },
                homeRefreshRequested = homeRefreshPending,
                onHomeRefreshHandled = { homeRefreshPending = false },
                onOpenAccountSettings = { navController.navigate(AccountSettingsRoute) },
                onOpenCreateGroup = { navController.navigate(CreateGroupRoute) },
                onOpenDiscoverGroups = { navController.navigate(DiscoverGroupsRoute) },
                onLogout = onLogout
            )
            NavHost(navController = navController, startDestination = ShellRoute) {
                // 셸은 NavHost 밖에서 항상 살아있다 — 시작 목적지는 빈 오버레이
                composable<ShellRoute> {}
                composable<GroupDetailRoute> { backStackEntry ->
                    val route = backStackEntry.toRoute<GroupDetailRoute>()
                    // 작성 화면이 남긴 결과 수신 — 그룹 피드는 화면이 lazyPagingItems.refresh()로 갱신
                    val postCreated by backStackEntry.savedStateHandle
                        .getStateFlow(POST_CREATED_KEY, false)
                        .collectAsState()

                    // Surface가 아래 셸로의 터치 전파를 막는다(오버레이 목적지 공통)
                    Surface(color = SgTheme.colors.paper) {
                        GroupDetailScreen(
                            groupId = route.groupId,
                            onBack = { navController.popBackStack() },
                            onCreatePost = { navController.navigate(CreatePostRoute(groupId = route.groupId)) },
                            refreshRequested = postCreated,
                            onRefreshHandled = { backStackEntry.savedStateHandle[POST_CREATED_KEY] = false },
                            // 풀스크린이라 하단 시스템 내비바 인셋을 화면이 직접 소화
                            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                        )
                    }
                }
                composable<AccountSettingsRoute> {
                    Surface(color = SgTheme.colors.paper) {
                        AccountSettingsScreen(
                            onBack = { navController.popBackStack() },
                            // 풀스크린이라 하단 시스템 내비바 인셋을 화면이 직접 소화
                            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                        )
                    }
                }
                composable<CreateGroupRoute> {
                    Surface(color = SgTheme.colors.paper) {
                        CreateGroupScreen(
                            onBack = { navController.popBackStack() },
                            // 풀스크린이라 하단 시스템 내비바 인셋을 화면이 직접 소화
                            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                        )
                    }
                }
                composable<DiscoverGroupsRoute> {
                    Surface(color = SgTheme.colors.paper) {
                        DiscoverGroupsScreen(
                            onBack = { navController.popBackStack() },
                            // 풀스크린이라 하단 시스템 내비바 인셋을 화면이 직접 소화
                            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                        )
                    }
                }
                composable<CreatePostRoute> { backStackEntry ->
                    val route = backStackEntry.toRoute<CreatePostRoute>()

                    Surface(color = SgTheme.colors.paper) {
                        CreatePostScreen(
                            groupId = route.groupId,
                            onBack = { navController.popBackStack() },
                            onCreated = {
                                if (route.groupId == null) {
                                    homeRefreshPending = true
                                } else {
                                    navController.previousBackStackEntry
                                        ?.savedStateHandle?.set(POST_CREATED_KEY, true)
                                }
                                navController.popBackStack()
                            },
                            // 풀스크린이라 하단 시스템 내비바 인셋을 화면이 직접 소화
                            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                        )
                    }
                }
            }
        }
    }
}

private enum class AuthScreen { LOGIN, REGISTER }

/** 로그인 ↔ 가입 전환 — 가입 성공 시 웹과 동일하게 안내 문구와 함께 로그인으로 복귀 */
@Composable
private fun AuthFlow() {
    var authScreen by remember { mutableStateOf(AuthScreen.LOGIN) }
    var justRegistered by remember { mutableStateOf(false) }

    when (authScreen) {
        AuthScreen.LOGIN -> LoginScreen(
            justRegistered = justRegistered,
            onNavigateToRegister = {
                justRegistered = false
                authScreen = AuthScreen.REGISTER
            }
        )
        AuthScreen.REGISTER -> RegisterScreen(
            onRegistered = {
                justRegistered = true
                authScreen = AuthScreen.LOGIN
            },
            onNavigateToLogin = { authScreen = AuthScreen.LOGIN }
        )
    }
}
