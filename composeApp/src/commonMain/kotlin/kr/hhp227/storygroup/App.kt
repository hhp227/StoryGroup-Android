package kr.hhp227.storygroup

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import kr.hhp227.storygroup.di.AppContainer
import kr.hhp227.storygroup.di.LocalAppContainer
import kr.hhp227.storygroup.di.LocalSessionViewModelStoreOwner
import kr.hhp227.storygroup.di.sessionViewModel
import kr.hhp227.storygroup.ui.rtc.IncomingCallBanner
import kr.hhp227.storygroup.ui.rtc.IncomingCallViewModel
import kr.hhp227.storygroup.ui.screens.auth.LoginScreen
import kr.hhp227.storygroup.ui.screens.auth.LoginViewModel
import kr.hhp227.storygroup.ui.screens.auth.RegisterScreen
import kr.hhp227.storygroup.ui.screens.call.CallScreen
import kr.hhp227.storygroup.ui.screens.chat.ChatRoomScreen
import kr.hhp227.storygroup.ui.screens.group.CreateGroupScreen
import kr.hhp227.storygroup.ui.screens.group.DiscoverGroupsScreen
import kr.hhp227.storygroup.ui.screens.group.GroupDetailScreen
import kr.hhp227.storygroup.ui.screens.group.GroupEditScreen
import kr.hhp227.storygroup.ui.screens.post.CreatePostScreen
import kr.hhp227.storygroup.ui.screens.post.PostDetailScreen
import kr.hhp227.storygroup.ui.screens.search.SearchScreen
import kr.hhp227.storygroup.ui.screens.settings.AccountSettingsScreen
import kr.hhp227.storygroup.ui.screens.settings.AppSettingsScreen
import kr.hhp227.storygroup.ui.screens.user.UserProfileScreen
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

/** 채팅방 — 그룹 채팅(groupId 있음)/DM(null) 공용. title은 허브가 아는 표시명(그룹명/상대 이름) */
@Serializable
internal data class ChatRoomRoute(val chatRoomId: Long, val groupId: Long?, val title: String)

/**
 * 게시글 작성 — groupId null이면 라운지(홈 피드)에 게시(웹 메인 피드 폼 미러).
 * postId가 있으면 같은 폼이 수정 모드로 동작한다(수정은 그 글의 groupId로 들어오므로 groupId도 항상 있다).
 */
@Serializable
internal data class CreatePostRoute(val groupId: Long?, val postId: Long? = null)

/**
 * 게시글 상세 — 본문·좋아요·댓글(웹 /groups/{id}/posts/{postId} 미러).
 * 라운지 글도 라운지 그룹 id로 들어오므로 홈·그룹 피드가 같은 목적지를 쓴다.
 */
@Serializable
internal data class PostDetailRoute(val groupId: Long, val postId: Long)

/** 계정 설정 — 프로필 수정+비밀번호 변경(웹 /settings/profile·password 미러) */
@Serializable
internal data object AccountSettingsRoute

/** 그룹 정보 수정 — 설정 탭 메뉴에서 진입(계정 설정과 같은 셸 위 풀스크린) */
@Serializable
internal data class GroupEditRoute(val groupId: Long)

/** 앱 설정 — 셸 내부 오버레이 외에 그룹 상세(오버레이 목적지) 위에서도 열 수 있는 라우트 */
@Serializable
internal data object AppSettingsRoute

/** 그룹 만들기 — 이름/소개/커버 이미지+가입 방식 */
@Serializable
internal data object CreateGroupRoute

/** 그룹 찾기 — 검색+정렬, 카드 탭 시 상세 다이얼로그에서 가입/신청(웹 그룹 찾기 탭 미러) */
@Serializable
internal data object DiscoverGroupsRoute

/** 홈 통합검색 — 웹 /search 미러(5섹션). 홈 상단바 검색 아이콘으로 진입 */
@Serializable
internal data object SearchRoute

/** 공개 프로필 — 게시글 작성자·검색 users·친구 행에서 진입(웹 /users/[userId] 미러) */
@Serializable
internal data class UserProfileRoute(val userId: Long)

/**
 * 방 통화 — DM 1:1·그룹 방 공용(페이스톡 미러, 채팅방 세션에 통화가 붙는다).
 * ring=true는 발신(입장+벨울림 — 그룹 방은 서버가 방 멤버 전원 팬아웃), false는 수신 배너
 * 수락으로 진입. title은 호출 측이 아는 표시명(DM=상대 이름, 그룹 방=그룹/방 이름)
 */
@Serializable
internal data class CallRoute(
    val chatRoomId: Long,
    val title: String,
    val ring: Boolean,
    // false면 보이스톡(카메라 OFF·수화구 시작) — CALL_INVITE에 통화 종류가 없어 수신(배너 수락)은 항상 기본값
    val video: Boolean = true
)

/** 그룹 피드 작성 성공을 이전 백스택 엔트리(그룹 상세)로 알리는 결과 키 — Paging-CRUD 샘플 미러 */
internal const val POST_CREATED_KEY = "post_created"

/** 그룹 정보 수정 성공을 이전 백스택 엔트리(그룹 상세)로 알리는 결과 키 — POST_CREATED_KEY 패턴 */
internal const val GROUP_UPDATED_KEY = "group_updated"

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
        // 그룹 상세에서 나가기/삭제 성공 신호 — 셸의 그룹 탭이 소비해 목록을 다시 읽는다(홈 미러)
        var groupsRefreshPending by remember { mutableStateOf(false) }
        // 수신 통화 배너(DM·그룹 방) — 개인 큐(공유 소켓)의 CALL_INVITE를 세션 전역에서 받는다
        val incomingCallViewModel = sessionViewModel { IncomingCallViewModel(it.observePersonalEventsUseCase) }
        val incomingCallUiState by incomingCallViewModel.uiState.collectAsState()

        Box {
            MainShell(
                themeState = themeState,
                onOpenGroupDetail = { group -> navController.navigate(GroupDetailRoute(group.id)) },
                onCreatePost = { navController.navigate(CreatePostRoute(groupId = null)) },
                onOpenPostDetail = { groupId, postId -> navController.navigate(PostDetailRoute(groupId, postId)) },
                onOpenChatRoom = { chatRoomId, groupId, title ->
                    navController.navigate(ChatRoomRoute(chatRoomId, groupId, title))
                },
                homeRefreshRequested = homeRefreshPending,
                onHomeRefreshHandled = { homeRefreshPending = false },
                groupsRefreshRequested = groupsRefreshPending,
                onGroupsRefreshHandled = { groupsRefreshPending = false },
                onOpenAccountSettings = { navController.navigate(AccountSettingsRoute) },
                onOpenCreateGroup = { navController.navigate(CreateGroupRoute) },
                onOpenDiscoverGroups = { navController.navigate(DiscoverGroupsRoute) },
                onOpenSearch = { navController.navigate(SearchRoute) },
                onOpenUserProfile = { userId -> navController.navigate(UserProfileRoute(userId)) },
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
                    // 수정 화면이 남긴 결과 수신 — 상세·설정 탭이 그룹을 다시 읽는다
                    val groupUpdated by backStackEntry.savedStateHandle
                        .getStateFlow(GROUP_UPDATED_KEY, false)
                        .collectAsState()

                    // Surface가 아래 셸로의 터치 전파를 막는다(오버레이 목적지 공통)
                    Surface(color = SgTheme.colors.paper) {
                        GroupDetailScreen(
                            groupId = route.groupId,
                            onBack = { navController.popBackStack() },
                            onCreatePost = { navController.navigate(CreatePostRoute(groupId = route.groupId)) },
                            onOpenPostDetail = { postId -> navController.navigate(PostDetailRoute(route.groupId, postId)) },
                            // 상단바 채팅 버튼(기본 방)과 멤버 스트립 DM — 셸의 채팅 허브와 같은 라우트로 들어간다
                            onOpenChatRoom = { chatRoomId, groupId, title ->
                                navController.navigate(ChatRoomRoute(chatRoomId, groupId, title))
                            },
                            refreshRequested = postCreated,
                            onRefreshHandled = { backStackEntry.savedStateHandle[POST_CREATED_KEY] = false },
                            onGroupClosed = {
                                // 나간/삭제한 그룹이 목록에 남지 않게 — 셸의 그룹 탭이 신호를 소비해 refresh한다
                                groupsRefreshPending = true
                                navController.popBackStack()
                            },
                            groupUpdateRequested = groupUpdated,
                            onGroupUpdateHandled = { backStackEntry.savedStateHandle[GROUP_UPDATED_KEY] = false },
                            onOpenGroupEdit = { navController.navigate(GroupEditRoute(route.groupId)) },
                            onOpenAccountSettings = { navController.navigate(AccountSettingsRoute) },
                            onOpenAppSettings = { navController.navigate(AppSettingsRoute) },
                            // 풀스크린이라 하단 시스템 내비바 인셋을 화면이 직접 소화
                            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                        )
                    }
                }
                composable<ChatRoomRoute> { backStackEntry ->
                    val route = backStackEntry.toRoute<ChatRoomRoute>()

                    Surface(color = SgTheme.colors.paper) {
                        ChatRoomScreen(
                            chatRoomId = route.chatRoomId,
                            groupId = route.groupId,
                            title = route.title,
                            onBack = { navController.popBackStack() },
                            // 통화 발신 — 입장+벨울림(DM=상대 1명, 그룹 방=방 멤버 팬아웃, 보이스톡·페이스톡 미러).
                            // 진행 중 통화 합류면 서버가 다시 울리지 않는다
                            onStartCall = { video ->
                                navController.navigate(CallRoute(route.chatRoomId, route.title, ring = true, video = video))
                            },
                            // 풀스크린이라 하단 시스템 내비바 인셋을 화면이 직접 소화
                            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                        )
                    }
                }
                composable<CallRoute> { backStackEntry ->
                    val route = backStackEntry.toRoute<CallRoute>()

                    Surface(color = SgTheme.colors.paper) {
                        CallScreen(
                            chatRoomId = route.chatRoomId,
                            title = route.title,
                            ring = route.ring,
                            video = route.video,
                            onBack = { navController.popBackStack() },
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
                composable<GroupEditRoute> { backStackEntry ->
                    val route = backStackEntry.toRoute<GroupEditRoute>()

                    Surface(color = SgTheme.colors.paper) {
                        GroupEditScreen(
                            groupId = route.groupId,
                            onBack = { navController.popBackStack() },
                            onSaved = {
                                // 상세가 커버·제목을 다시 읽게 결과를 남기고 닫는다(CreatePost 결과 패턴)
                                navController.previousBackStackEntry?.savedStateHandle?.set(GROUP_UPDATED_KEY, true)
                                // 목록 카드의 이름·커버도 갱신되게 — 셸 그룹 탭이 신호를 소비한다
                                groupsRefreshPending = true
                                navController.popBackStack()
                            },
                            // 풀스크린이라 하단 시스템 내비바 인셋을 화면이 직접 소화
                            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                        )
                    }
                }
                composable<AppSettingsRoute> {
                    Surface(color = SgTheme.colors.paper) {
                        // 셸 내부(프로필 탭)와 같은 화면 — 그룹 상세 위에서 열 때는 NavHost 목적지로 띄운다
                        AppSettingsScreen(themeState = themeState, onBack = { navController.popBackStack() })
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
                composable<SearchRoute> {
                    Surface(color = SgTheme.colors.paper) {
                        SearchScreen(
                            onBack = { navController.popBackStack() },
                            onOpenGroupDetail = { groupId -> navController.navigate(GroupDetailRoute(groupId)) },
                            onOpenPostDetail = { groupId, postId -> navController.navigate(PostDetailRoute(groupId, postId)) },
                            onOpenChatRoom = { chatRoomId, groupId, title ->
                                navController.navigate(ChatRoomRoute(chatRoomId, groupId, title))
                            },
                            onOpenUserProfile = { userId -> navController.navigate(UserProfileRoute(userId)) },
                            // 풀스크린이라 하단 시스템 내비바 인셋을 화면이 직접 소화
                            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                        )
                    }
                }
                // 풀스크린이 아니라 카드 다이얼로그 — 뒤 화면이 스크림 너머로 남는다(iOS 시트 미러).
                // DM·프로필 수정으로 navigate하면 Navigation이 다이얼로그를 자동 pop하고 목적지를 연다
                dialog<UserProfileRoute>(
                    dialogProperties = DialogProperties(usePlatformDefaultWidth = false)
                ) { backStackEntry ->
                    val route = backStackEntry.toRoute<UserProfileRoute>()

                    UserProfileScreen(
                        userId = route.userId,
                        onClose = { navController.popBackStack() },
                        onOpenChatRoom = { chatRoomId, groupId, title ->
                            navController.navigate(ChatRoomRoute(chatRoomId, groupId, title))
                        },
                        onOpenAccountSettings = { navController.navigate(AccountSettingsRoute) },
                        // usePlatformDefaultWidth=false라 폭은 화면이 직접 잡는다(폰=92%, 데스크탑=최대 420dp)
                        modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(0.92f)
                    )
                }
                composable<PostDetailRoute> { backStackEntry ->
                    val route = backStackEntry.toRoute<PostDetailRoute>()
                    // 수정 화면이 남긴 결과 수신 — 돌아오면 상세를 다시 읽어 바뀐 본문을 보여준다
                    val postUpdated by backStackEntry.savedStateHandle
                        .getStateFlow(POST_CREATED_KEY, false)
                        .collectAsState()

                    Surface(color = SgTheme.colors.paper) {
                        PostDetailScreen(
                            groupId = route.groupId,
                            postId = route.postId,
                            onBack = { navController.popBackStack() },
                            onEdit = { navController.navigate(CreatePostRoute(route.groupId, route.postId)) },
                            onOpenUserProfile = { userId -> navController.navigate(UserProfileRoute(userId)) },
                            refreshRequested = postUpdated,
                            onRefreshHandled = { backStackEntry.savedStateHandle[POST_CREATED_KEY] = false },
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
                            postId = route.postId,
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
            // DM 수신 통화 배너 — 어떤 화면 위에서든 뜬다(Box의 마지막 자식 = 최상단, 웹 헤더 배너 미러)
            incomingCallUiState.incomingCall?.let { call ->
                IncomingCallBanner(
                    call = call,
                    onAccept = {
                        incomingCallViewModel.onAction(IncomingCallViewModel.Action.Dismiss)
                        // 수락 = 통화 화면 진입(구독=입장) — 벨울림은 다시 보내지 않는다(ring=false).
                        // 제목은 그룹 방이면 방(그룹) 이름, DM이면 발신자 이름(채팅방 라우트와 동일 규칙).
                        // 보이스톡이면 수신 측도 카메라 OFF로 입장한다(발신 모드 미러)
                        navController.navigate(
                            CallRoute(call.chatRoomId, call.roomName ?: call.callerName, ring = false, video = call.video)
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
