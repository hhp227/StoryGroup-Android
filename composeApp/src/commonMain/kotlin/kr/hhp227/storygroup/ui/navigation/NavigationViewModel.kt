package kr.hhp227.storygroup.ui.navigation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kr.hhp227.storygroup.ui.mvi.MviViewModel

/**
 * 네비게이션 — 탭과 화면 간 결과는 상태로 소유하고, 오버레이 이동만 이벤트로 호스트에 위임한다.
 * 백스택 자체는 NavHost(Compose)/NavigationStack(iOS)가 소유한다 — 시스템 뒤로가기와
 * 프로세스 사망 복원은 플랫폼이 이미 잘하는 일이라 다시 만들지 않는다.
 * ⚠️ 이건 백스택 얘기고 currentTab은 다르다: 이 VM은 SavedStateHandle을 안 받는 평범한
 * MutableStateFlow라 currentTab은 프로세스 사망을 못 버틴다(옛 셸의 rememberSaveable은 버텼다).
 * 알고 있는 트레이드오프 — MainShell.kt 문서 참조.
 * 세션 스코프에 선언한다 — 로그아웃 시 pendingResults까지 함께 정리된다.
 * iosApp UI/Navigation/NavigationViewModel.swift와 1:1 미러.
 */
class NavigationViewModel : ViewModel(),
    MviViewModel<NavigationViewModel.UiState, NavigationAction, NavigationEvent> {

    private val _uiState = MutableStateFlow(UiState())
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /**
     * MviViewModel 규약의 버퍼 1칸은 화면 1개가 액션 1개당 이벤트 1개를 내는 것을 전제한다.
     * NavigationViewModel은 모든 화면이 공유하는 허브라 한 onAction 호출 안에서(또는 호출자가
     * 미처 못 비운 사이) NavigateBack 다음에 NavigateTo가 곧장 이어지는 등 한 틱에 이벤트가
     * 몰릴 수 있다 — 버퍼 1칸+DROP_OLDEST면 뒤 이벤트가 앞 이벤트를 자리 하나를 두고 계속
     * 밀어내 소리 없이 사라진다. 그래서 8칸으로 넉넉히 늘린다.
     * replay = 0은 그대로다 — 나중에 붙는 구독자는 여전히 아무것도 못 받는다. 화면 간 결과를
     * event가 아니라 pendingResults(상태)로 다뤄야 하는 이유는 이 변경과 무관하게 그대로 유효하다.
     * tryEmit은 여전히 논블로킹이라 코루틴이 매달리는 일은 없다.
     */
    private val _event = MutableSharedFlow<NavigationEvent>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val event: Flow<NavigationEvent> = _event.asSharedFlow()

    override fun onAction(action: NavigationAction) {
        when (action) {
            // 탭은 상태다 — 셸이 uiState.currentTab을 그린다
            is NavigationAction.SelectTab ->
                _uiState.update { it.copy(currentTab = action.destination) }
            is NavigationAction.PublishResult ->
                _uiState.update { it.copy(pendingResults = it.pendingResults + action.result) }
            is NavigationAction.ConsumeResult ->
                _uiState.update { it.copy(pendingResults = it.pendingResults - action.result) }
            NavigationAction.NavigateBack -> emit(NavigationEvent.NavigateBack)
            is NavigationAction.OpenChatRoomFromProfile ->
                if (action.underlyingChatRoomId == action.chatRoomId) {
                    emit(NavigationEvent.NavigateBack)
                } else {
                    emit(navigateTo(Route.ChatRoom(action.chatRoomId, action.groupId, action.title)))
                }
            is NavigationAction.NavigateToGroupDetail ->
                emit(navigateTo(Route.GroupDetail(action.groupId)))
            is NavigationAction.NavigateToPostDetail ->
                emit(navigateTo(Route.PostDetail(action.groupId, action.postId)))
            is NavigationAction.NavigateToChatRoom ->
                emit(navigateTo(Route.ChatRoom(action.chatRoomId, action.groupId, action.title)))
            is NavigationAction.NavigateToUserProfile ->
                emit(navigateTo(Route.UserProfile(action.userId)))
            is NavigationAction.NavigateToCreatePost ->
                emit(navigateTo(Route.CreatePost(action.groupId, action.postId)))
            is NavigationAction.NavigateToGroupEdit ->
                emit(navigateTo(Route.GroupEdit(action.groupId)))
            is NavigationAction.NavigateToGroupReports ->
                emit(navigateTo(Route.GroupReports(action.groupId)))
            is NavigationAction.StartCall ->
                emit(navigateTo(Route.Call(action.chatRoomId, action.title, ring = true, video = action.video)))
            is NavigationAction.AcceptIncomingCall ->
                emit(navigateTo(Route.Call(action.chatRoomId, action.title, ring = false, video = action.video)))
            NavigationAction.NavigateToAccountSettings -> emit(navigateTo(Route.AccountSettings))
            NavigationAction.NavigateToAppSettings -> emit(navigateTo(Route.AppSettings))
            NavigationAction.NavigateToBlockedUsers -> emit(navigateTo(Route.BlockedUsers))
            NavigationAction.NavigateToCreateGroup -> emit(navigateTo(Route.CreateGroup))
            NavigationAction.NavigateToDiscoverGroups -> emit(navigateTo(Route.DiscoverGroups))
            NavigationAction.NavigateToPendingGroups -> emit(navigateTo(Route.PendingGroups))
            NavigationAction.NavigateToSearch -> emit(navigateTo(Route.Search))
        }
    }

    private fun navigateTo(route: Route): NavigationEvent = NavigationEvent.NavigateTo(route)

    /** tryEmit — 구독 전 발화에도 코루틴이 매달리지 않는다(MviViewModel 규약) */
    private fun emit(event: NavigationEvent) {
        _event.tryEmit(event)
    }

    data class UiState(
        val currentTab: MainDestination = MainDestination.HOME,
        /** 화면이 재진입해 읽어갈 때까지 남는다 — savedStateHandle 키의 일반화 */
        val pendingResults: Set<NavResult> = emptySet()
    )
}
