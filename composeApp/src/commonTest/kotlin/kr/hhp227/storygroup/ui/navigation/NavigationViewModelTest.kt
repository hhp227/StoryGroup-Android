package kr.hhp227.storygroup.ui.navigation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NavigationViewModelTest {

    /** event는 replay 0이라 수집기를 먼저 띄워야 한다 */
    private suspend fun collectEvents(
        viewModel: NavigationViewModel,
        scope: kotlinx.coroutines.CoroutineScope
    ): MutableList<NavigationEvent> {
        val received = mutableListOf<NavigationEvent>()

        scope.launch { viewModel.event.collect { received += it } }
        yield()
        return received
    }

    @Test
    fun navigateActionsMapToRoutes() = runTest {
        val viewModel = NavigationViewModel()
        val events = collectEvents(viewModel, backgroundScope)

        viewModel.onAction(NavigationAction.NavigateToGroupDetail(3L))
        viewModel.onAction(NavigationAction.NavigateToPostDetail(3L, 9L))
        viewModel.onAction(NavigationAction.NavigateToUserProfile(5L))
        viewModel.onAction(NavigationAction.NavigateToSearch)
        yield()

        assertEquals(
            listOf<NavigationEvent>(
                NavigationEvent.NavigateTo(Route.GroupDetail(3L)),
                NavigationEvent.NavigateTo(Route.PostDetail(3L, 9L)),
                NavigationEvent.NavigateTo(Route.UserProfile(5L)),
                NavigationEvent.NavigateTo(Route.Search)
            ),
            events
        )
    }

    /** 발신은 벨을 울리고(ring=true), 수신 수락은 입장만 한다 — 서버가 다시 울리지 않는다 */
    @Test
    fun callActionsSetRingFlag() = runTest {
        val viewModel = NavigationViewModel()
        val events = collectEvents(viewModel, backgroundScope)

        viewModel.onAction(NavigationAction.StartCall(1L, "방", video = false))
        viewModel.onAction(NavigationAction.AcceptIncomingCall(1L, "방", video = true))
        yield()

        assertEquals(
            listOf<NavigationEvent>(
                NavigationEvent.NavigateTo(Route.Call(1L, "방", ring = true, video = false)),
                NavigationEvent.NavigateTo(Route.Call(1L, "방", ring = false, video = true))
            ),
            events
        )
    }

    @Test
    fun selectTabUpdatesStateWithoutEvent() = runTest {
        val viewModel = NavigationViewModel()
        val events = collectEvents(viewModel, backgroundScope)

        viewModel.onAction(NavigationAction.SelectTab(MainDestination.CHAT))
        yield()

        assertEquals(MainDestination.CHAT, viewModel.uiState.value.currentTab)
        assertTrue(events.isEmpty(), "탭은 상태다 — 이동 이벤트를 내면 안 된다")
    }

    /**
     * 결과 신호는 이벤트가 아니라 상태다 — 글쓰기가 떠 있는 동안 그룹 상세는 컴포지션에서
     * 빠져 있어 이벤트를 받을 수 없다. 돌아와서 읽어갈 때까지 남아야 한다.
     * 두 종류를 동시에 담아 하나만 소비 — pendingResults가 Set이 아니라 단일 값처럼
     * 잘못 다뤄지면(예: 소비 시 전체를 비움) 나머지 하나가 같이 사라진다. 그 케이스를 잡는다
     */
    @Test
    fun resultsStayPendingUntilConsumed() = runTest {
        val viewModel = NavigationViewModel()
        val postCreated = NavResult.PostCreated(groupId = 4L)
        val groupsChanged = NavResult.GroupsChanged

        viewModel.onAction(NavigationAction.PublishResult(postCreated))
        viewModel.onAction(NavigationAction.PublishResult(groupsChanged))
        assertEquals(setOf(postCreated, groupsChanged), viewModel.uiState.value.pendingResults)

        viewModel.onAction(NavigationAction.ConsumeResult(postCreated))
        assertEquals(setOf(groupsChanged), viewModel.uiState.value.pendingResults, "소비 안 한 결과는 살아남아야 한다")
    }

    /** 채팅방 위에서 연 프로필이 같은 방으로 가면 방을 또 쌓지 않고 닫기만 한다 */
    @Test
    fun openChatRoomFromProfileGuardsSameRoom() = runTest {
        val viewModel = NavigationViewModel()
        val events = collectEvents(viewModel, backgroundScope)

        viewModel.onAction(
            NavigationAction.OpenChatRoomFromProfile(
                chatRoomId = 10L, groupId = null, title = "상대", underlyingChatRoomId = 10L
            )
        )
        yield()

        assertEquals(listOf<NavigationEvent>(NavigationEvent.NavigateBack), events)
    }

    @Test
    fun openChatRoomFromProfileNavigatesToDifferentRoom() = runTest {
        val viewModel = NavigationViewModel()
        val events = collectEvents(viewModel, backgroundScope)

        viewModel.onAction(
            NavigationAction.OpenChatRoomFromProfile(
                chatRoomId = 11L, groupId = null, title = "상대", underlyingChatRoomId = 10L
            )
        )
        yield()

        assertEquals(listOf<NavigationEvent>(NavigationEvent.NavigateTo(Route.ChatRoom(11L, null, "상대"))), events)
    }

    /**
     * 나머지 9개 목적지 매핑 + NavigateBack — 전부 `data object`라 `NavigateToAppSettings ->
     * Route.AccountSettings` 같은 복붙 실수가 컴파일도 통과하고 경고도 없다. "설정 버튼이
     * 엉뚱한 화면을 연다"로만 드러나는 회귀라 표로 못박아 둔다.
     */
    @Test
    fun remainingNavigateActionsMapToRoutesOrBack() = runTest {
        val cases = listOf<Pair<NavigationAction, NavigationEvent>>(
            NavigationAction.NavigateToChatRoom(chatRoomId = 7L, groupId = 3L, title = "채팅방") to
                NavigationEvent.NavigateTo(Route.ChatRoom(7L, 3L, "채팅방")),
            NavigationAction.NavigateToCreatePost(groupId = 3L, postId = null) to
                NavigationEvent.NavigateTo(Route.CreatePost(3L, null)),
            NavigationAction.NavigateToGroupEdit(groupId = 3L) to
                NavigationEvent.NavigateTo(Route.GroupEdit(3L)),
            NavigationAction.NavigateToGroupReports(groupId = 3L) to
                NavigationEvent.NavigateTo(Route.GroupReports(3L)),
            NavigationAction.NavigateToAccountSettings to NavigationEvent.NavigateTo(Route.AccountSettings),
            NavigationAction.NavigateToAppSettings to NavigationEvent.NavigateTo(Route.AppSettings),
            NavigationAction.NavigateToBlockedUsers to NavigationEvent.NavigateTo(Route.BlockedUsers),
            NavigationAction.NavigateToCreateGroup to NavigationEvent.NavigateTo(Route.CreateGroup),
            NavigationAction.NavigateToDiscoverGroups to NavigationEvent.NavigateTo(Route.DiscoverGroups),
            NavigationAction.NavigateBack to NavigationEvent.NavigateBack
        )
        val viewModel = NavigationViewModel()
        val events = collectEvents(viewModel, backgroundScope)

        // NavigationViewModel의 이벤트 버퍼는 8칸(DROP_OLDEST) — 트레일링 yield() 없이 8개 넘게
        // 쏘면 가장 오래된 것부터 조용히 사라진다(실측 확인: 9개를 몰아 쏘면 0번째가, 10개면
        // 0·1번째가 증발한다). 그래서 버퍼 폭 단위로 나눠 쏘고 묶음마다 yield 한 번 — 그래도
        // 액션 하나당 즉시 소비하지 않고 여러 개를 몰아 쏘는 구간은 남아 있어서, 버퍼가 1칸으로
        // 되돌아가는 회귀라면(둘째 액션부터 바로 깨진다) 이 테스트가 그대로 잡아낸다
        cases.chunked(8).forEach { chunk ->
            chunk.forEach { (action, _) -> viewModel.onAction(action) }
            yield()
        }

        assertEquals(cases.map { it.second }, events)
    }
}
