package kr.hhp227.storygroup

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import kr.hhp227.storygroup.di.AppContainer
import kr.hhp227.storygroup.di.LocalAppContainer
import kr.hhp227.storygroup.di.LocalSessionViewModelStoreOwner
import kr.hhp227.storygroup.di.sessionViewModel
import kr.hhp227.storygroup.ui.components.NetworkStatusBanner
import kr.hhp227.storygroup.ui.components.NetworkStatusViewModel
import kr.hhp227.storygroup.ui.navigation.AppNavHost
import kr.hhp227.storygroup.ui.navigation.MainDestination
import kr.hhp227.storygroup.ui.navigation.NavigationAction
import kr.hhp227.storygroup.ui.navigation.NavigationEvent
import kr.hhp227.storygroup.ui.navigation.PaneMode
import kr.hhp227.storygroup.ui.navigation.currentPaneRouteAsState
import kr.hhp227.storygroup.ui.navigation.paneModeFor
import kr.hhp227.storygroup.ui.navigation.sessionNavigationViewModel
import kr.hhp227.storygroup.ui.rtc.IncomingCallBanner
import kr.hhp227.storygroup.ui.rtc.IncomingCallViewModel
import kr.hhp227.storygroup.ui.screens.auth.LoginScreen
import kr.hhp227.storygroup.ui.screens.auth.LoginViewModel
import kr.hhp227.storygroup.ui.screens.auth.RegisterScreen
import kr.hhp227.storygroup.ui.shell.MainShell
import kr.hhp227.storygroup.ui.theme.NightMode
import kr.hhp227.storygroup.ui.theme.SgTheme
import kr.hhp227.storygroup.ui.theme.StoryGroupTheme
import kr.hhp227.storygroup.ui.theme.ThemeState

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

            // 네트워크 배너 — 로그인 화면 포함 전역 오버레이(ConCafe App.kt 미러)라 세션 아닌 앱 루트 VM
            val networkStatusViewModel = viewModel {
                NetworkStatusViewModel(container.observeNetworkAlertStateUseCase)
            }
            val networkUiState by networkStatusViewModel.uiState.collectAsState()

            Box(Modifier.fillMaxSize()) {
                if (loginUiState.isLoggedIn) {
                    SessionContent(
                        themeState = themeState,
                        onLogout = { loginViewModel.onAction(LoginViewModel.Action.Logout) }
                    )
                } else {
                    AuthFlow()
                }
                NetworkStatusBanner(
                    networkAlertState = networkUiState.networkAlertState,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }
    }
}

/** 좌측 셸 + 우측 상세로 나누는 최소 폭 — 레일(600dp)보다 넉넉히 위 */
private val TwoPaneBreakpoint = 900.dp

/**
 * 로그인 세션 서브트리 — 세션 스코프 ViewModelStore를 제공하고, 로그아웃(dispose) 시 일괄 clear.
 * 네비게이션은 NavigationViewModel이 소유한다 — 여기엔 이벤트 → NavController 배선만 남는다.
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
        val navigationViewModel = sessionNavigationViewModel()
        val navUiState by navigationViewModel.uiState.collectAsState()
        val navController = rememberNavController()
        val onNavigationAction = navigationViewModel::onAction
        // 수신 통화 배너(DM·그룹 방) — 개인 큐(공유 소켓)의 CALL_INVITE를 세션 전역에서 받는다
        val incomingCallViewModel = sessionViewModel { IncomingCallViewModel(it.observePersonalEventsUseCase) }
        val incomingCallUiState by incomingCallViewModel.uiState.collectAsState()

        LaunchedEffect(navigationViewModel) {
            navigationViewModel.event.collect { event ->
                when (event) {
                    is NavigationEvent.NavigateTo -> navController.navigate(event.route)
                    NavigationEvent.NavigateBack -> navController.popBackStack()
                }
            }
        }
        // 2-pane 판정용 목적지의 PaneMode — 다이얼로그가 떠 있으면 그 아래 목적지 기준(currentPaneRouteAsState
        // 참조). 셸만 보이는 상태(route == null)는 FULL_SCREEN 취급
        val paneRoute = navController.currentPaneRouteAsState()
        val pane = paneRoute?.let(::paneModeFor) ?: PaneMode.FULL_SCREEN

        BoxWithConstraints {
            val twoPane = maxWidth >= TwoPaneBreakpoint && pane == PaneMode.DETAIL_PANE

            // movableContentOf가 없으면 창 폭이 900dp를 넘나들 때마다 셸/NavHost 서브트리가 통째로
            // 재생성되어 백스택·스크롤 위치·Paging3 프레젠터가 전부 날아간다 — 같은 컴포지션 노드를
            // 부모만 바꿔 옮긴다. twoPane·currentTab처럼 리컴포지션마다 바뀌는 값은 이 remember 블록에
            // 캡처하지 않고 호출 시점 파라미터로 넘긴다 — 캡처하면 remember가 최초 1회만 실행되어 그
            // 시점 값에 고정되고, 이후 창 폭이 바뀌어도 갱신되지 않는다(고쳐 쓰지 말 것).
            val shell = remember {
                movableContentOf { m: Modifier, tab: MainDestination ->
                    MainShell(
                        themeState = themeState,
                        currentTab = tab,
                        onNavigationAction = onNavigationAction,
                        onLogout = onLogout,
                        modifier = m
                    )
                }
            }
            val host = remember {
                movableContentOf { m: Modifier ->
                    AppNavHost(
                        navController = navController,
                        themeState = themeState,
                        modifier = m
                    )
                }
            }

            if (twoPane) {
                // 좌측 셸(목록) + 구분선 + 우측 NavHost(상세) — NavHost는 셸을 덮지 않으므로 오버레이 아님
                Row(Modifier.fillMaxSize()) {
                    shell(Modifier.weight(1f), navUiState.currentTab)
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(SgTheme.colors.stoneBorder)
                    )
                    host(Modifier.weight(1f))
                }
            } else {
                // 기존과 같은 오버레이 배치 — NavHost가 셸 위를 덮는다
                Box(Modifier.fillMaxSize()) {
                    shell(Modifier.fillMaxSize(), navUiState.currentTab)
                    host(Modifier.fillMaxSize())
                }
            }

            // 수신 통화 배너 — 어떤 화면 위에서든 뜬다(BoxWithConstraints의 마지막 자식 = 최상단).
            // BoxWithConstraintsScope는 BoxScope를 상속하므로 align(Alignment.TopCenter)가 그대로 동작한다
            incomingCallUiState.incomingCall?.let { call ->
                IncomingCallBanner(
                    call = call,
                    onAccept = {
                        incomingCallViewModel.onAction(IncomingCallViewModel.Action.Dismiss)
                        onNavigationAction(
                            NavigationAction.AcceptIncomingCall(
                                chatRoomId = call.chatRoomId,
                                // 제목은 그룹 방이면 방(그룹) 이름, DM이면 발신자 이름(채팅방 라우트와 동일 규칙)
                                title = call.roomName ?: call.callerName,
                                // 보이스톡이면 수신 측도 카메라 OFF로 입장한다(발신 모드 미러)
                                video = call.video
                            )
                        )
                    },
                    onDecline = { incomingCallViewModel.onAction(IncomingCallViewModel.Action.Dismiss) },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
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
