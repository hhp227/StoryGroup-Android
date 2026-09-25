# 네비게이션 MVI 전환 설계 (Compose + iOS + Desktop 2-pane)

- 작성일: 2026-08-17
- 대상 리포: `StoryGroup-Android` (KMP)
- 참조 구조: ConCafe `presentation/navigation` (NavigationViewModel + Route + NavigationAction + NavigationEvent)

## 1. 배경과 목표

현재 StoryGroup의 네비게이션은 **콜백 람다 프롭 드릴링**이다.

- `App.kt` 520줄 — NavHost 목적지 15종의 배선이 전부 여기 있고, 각 화면에 `onBack`/`onOpenXxx` 람다를 개별로 넘긴다.
- `MainShell`/`TabShell`/`DrawerShell` 파라미터 약 20개. 화면 하나가 늘면 `App → MainShell → TabShell → DrawerShell → 화면` 5곳을 고쳐야 한다.
- iOS `MainShellView` 464줄 — `@State` 플래그 10개(`showSettings`, `selectedGroupId`, `selectedChatRoom`, …)와
  `navigationDestination(isPresented:)` 7개 + iOS 15용 `NavigationLink(isActive:)` 7개가 중복으로 나열돼 있다.

목표는 ConCafe와 같은 형태로 바꾸는 것이다.

- 화면은 `onNavigationAction: (NavigationAction) -> Unit` **하나만** 안다. `Route`를 몰라도 된다.
- 탭 선택도 Action으로 통일해 관측 가능한 상태로 만든다.
- 화면 간 결과 신호(`savedStateHandle` 키, `refreshPending` 플래그)를 걷어낸다.
- Desktop(PC)에서 목록↔상세를 2-pane으로 나눈다.

### 유지해야 하는 제약

- **셸은 NavHost 밖 keep-alive를 유지한다.** 전 목적지를 컴포지션에 유지하고 현재 것만 measure하는 현재 구조는
  Paging3 프레젠터(`collectAsLazyPagingItems`)와 `LazyListState`가 컴포지션에 살기 때문에 필요하다.
  셸을 NavHost 안으로 넣으면(ConCafe의 `Route.Main`) 이게 깨진다. → 셸은 그대로 두고 오버레이만 NavHost가 맡는다.
- **iOS 15 지원.** `MainShellView`에 `#available(iOS 16.0, *)` 폴백이 있다(iPhone 7). `NavigationStack(path:)`은 16+라
  ConCafe의 `NavigationStackCompat`을 포팅해야 한다.
- 커밋·push는 사용자가 직접 한다. 이 작업은 스테이징까지만 한다.

## 2. 소유권 모델 (채택안 = C안)

| 상태 | 소유자 | 이유 |
|---|---|---|
| 현재 탭 | `NavigationViewModel.UiState.currentTab` | 우리가 소유해야 하는 상태 — 관측·테스트 가능해야 함 |
| 오버레이 백스택 | Compose `NavHostController` / SwiftUI `path: [Route]` | 시스템 뒤로가기·프로세스 사망 복원은 플랫폼이 이미 잘한다 |
| 화면 간 결과 | `NavigationViewModel.UiState.pendingResults` | ↓ 3절 |
| 2-pane 판정 | 순수 함수 `paneModeFor(route)` | 3플랫폼 공유, 단위 테스트 가능 |

기각한 대안:

- **A안(VM은 순수 변환기, 탭은 셸 `remember`)** — 탭이 관측 불가라 2-pane 판정과 테스트가 어렵다.
- **B안(VM이 백스택까지 소유, NavHost 제거)** — ConCafe Desktop이 이 방식이지만, 그건 당시 navigation-compose가
  Android 전용이었기 때문이다. StoryGroup은 이미 멀티플랫폼판 + `@Serializable` 타입세이프 라우트를 쓴다. 퇴행.

## 3. 결과 신호를 Event가 아니라 상태로 두는 이유

**이 설계에서 가장 틀리기 쉬운 지점이다.**

`SharedFlow(replay = 0)`는 구독자가 없으면 값이 사라진다. 글쓰기 화면이 떠 있는 동안 그룹 상세는
NavHost 백스택에만 있고 **컴포지션에서 빠져 있다**(Navigation Compose는 현재 목적지만 컴포즈한다).
`NavigationEvent.PostCreated`를 방출해도 받을 구독자가 없어 그대로 유실된다.

현재 코드가 `savedStateHandle`을 쓰는 이유가 정확히 이것이다 — 돌아왔을 때 **상태로 남아 있어야** 한다.

```kotlin
data class UiState(
    val currentTab: MainDestination = MainDestination.HOME,
    /** 화면이 재진입해 읽어갈 때까지 남는다 — savedStateHandle 키/refreshPending 플래그의 일반화 */
    val pendingResults: Set<NavResult> = emptySet()
)

sealed interface NavResult {
    /** groupId == null 이면 라운지(홈 피드) */
    data class PostCreated(val groupId: Long?) : NavResult
    data class PostUpdated(val groupId: Long, val postId: Long) : NavResult
    data class GroupUpdated(val groupId: Long) : NavResult
    /** 나가기·삭제·수정으로 내 그룹 목록이 바뀜 */
    data object GroupsChanged : NavResult
}
```

소비 규약은 현재의 `refreshRequested` / `onRefreshHandled` 쌍과 모양이 같다.

```kotlin
val pending = navUiState.pendingResults.any { it is NavResult.PostCreated && it.groupId == groupId }

LaunchedEffect(pending) {
    if (pending) {
        lazyPagingItems.refresh()
        onNavigationAction(NavigationAction.ConsumeResult(NavResult.PostCreated(groupId)))
    }
}
```

셸(홈·그룹 목록)은 keep-alive라 항상 구독 상태다. 오버레이 목적지(그룹 상세·게시글 상세)는 재진입 시 읽는다.
두 경우 모두 위 코드 하나로 처리된다.

## 4. 공용 계약

위치: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/navigation/`

`MainDestination`(탭 enum)은 현재 `ui/shell/MainShell.kt`에 있는데, `NavigationAction.SelectTab`이 참조하므로
`ui/navigation/MainDestination.kt`로 옮긴다. 그러지 않으면 `navigation → shell` 역방향 의존이 생긴다.
셸은 이 enum을 계속 쓰되 import만 바뀐다.

### 4.1 Route.kt — `App.kt`에 흩어진 15종을 통합

```kotlin
@Serializable
sealed interface Route {
    @Serializable data object Shell : Route                       // NavHost startDestination(빈 오버레이)
    @Serializable data class GroupDetail(val groupId: Long) : Route
    @Serializable data class PostDetail(val groupId: Long, val postId: Long) : Route
    @Serializable data class ChatRoom(val chatRoomId: Long, val groupId: Long?, val title: String) : Route
    @Serializable data class UserProfile(val userId: Long) : Route
    @Serializable data class CreatePost(val groupId: Long?, val postId: Long? = null) : Route
    @Serializable data class GroupEdit(val groupId: Long) : Route
    @Serializable data class GroupReports(val groupId: Long) : Route
    @Serializable data class Call(
        val chatRoomId: Long, val title: String, val ring: Boolean, val video: Boolean = true
    ) : Route
    @Serializable data object AccountSettings : Route
    @Serializable data object AppSettings : Route
    @Serializable data object BlockedUsers : Route
    @Serializable data object CreateGroup : Route
    @Serializable data object DiscoverGroups : Route
    @Serializable data object Search : Route
}
```

`internal`이 아니라 `public`이어야 한다 — 현재 `App.kt`의 라우트가 `internal`인 것은 Desktop에서
kotlinx.serialization 리플렉션이 `private`에 접근하지 못해 죽었기 때문이고(기존 주석), 별도 패키지로 옮기면
`ui.navigation` 밖(화면들)에서 참조하므로 `public`이 맞다.

### 4.2 NavigationAction.kt — ConCafe식 목적지별 Action

화면이 `Route`를 몰라도 되게 의도 단위로 나눈다.

```kotlin
sealed interface NavigationAction {
    data class NavigateToGroupDetail(val groupId: Long) : NavigationAction
    data class NavigateToPostDetail(val groupId: Long, val postId: Long) : NavigationAction
    data class NavigateToChatRoom(val chatRoomId: Long, val groupId: Long?, val title: String) : NavigationAction
    data class NavigateToUserProfile(val userId: Long) : NavigationAction
    data class NavigateToCreatePost(val groupId: Long?, val postId: Long? = null) : NavigationAction
    data class NavigateToGroupEdit(val groupId: Long) : NavigationAction
    data class NavigateToGroupReports(val groupId: Long) : NavigationAction
    data class StartCall(val chatRoomId: Long, val title: String, val video: Boolean) : NavigationAction
    data class AcceptIncomingCall(val chatRoomId: Long, val title: String, val video: Boolean) : NavigationAction
    data object NavigateToAccountSettings : NavigationAction
    data object NavigateToAppSettings : NavigationAction
    data object NavigateToBlockedUsers : NavigationAction
    data object NavigateToCreateGroup : NavigationAction
    data object NavigateToDiscoverGroups : NavigationAction
    data object NavigateToSearch : NavigationAction
    data object NavigateBack : NavigationAction

    data class SelectTab(val destination: MainDestination) : NavigationAction

    /** 채팅방 위에서 연 프로필이 같은 방으로 가려 할 때 — 방을 또 쌓지 않고 닫기만 한다 */
    data class OpenChatRoomFromProfile(
        val chatRoomId: Long, val groupId: Long?, val title: String, val underlyingChatRoomId: Long?
    ) : NavigationAction

    data class PublishResult(val result: NavResult) : NavigationAction
    data class ConsumeResult(val result: NavResult) : NavigationAction
}
```

`StartCall`(발신, `ring = true`)과 `AcceptIncomingCall`(수신 수락, `ring = false`)을 나눠 `ring` 플래그를
호출부가 다루지 않게 한다 — 지금은 각 호출부가 `ring`을 직접 넘기고 있어 실수하기 쉽다.

### 4.3 NavigationEvent.kt

```kotlin
sealed interface NavigationEvent {
    data class NavigateTo(val route: Route) : NavigationEvent
    data object NavigateBack : NavigationEvent
}
```

두 개면 충분하다. 프로필 다이얼로그의 "같은 방 가드"는 같은 방이면 `NavigateBack`, 다른 방이면 `NavigateTo`로
갈리고, 다른 방으로 갈 때 다이얼로그는 Navigation이 알아서 pop한다(현재 동작). 별도의 `ReplaceTop`은 필요 없다.

`currentTab`은 Event가 아니라 `UiState`로 노출한다. 셸이 collect한다. 이동만 Event다.

### 4.4 NavigationViewModel.kt

```kotlin
class NavigationViewModel : ViewModel(),
    MviViewModel<NavigationViewModel.UiState, NavigationAction, NavigationEvent> {

    private val _uiState = MutableStateFlow(UiState())
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<NavigationEvent>(
        extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val event: Flow<NavigationEvent> = _event.asSharedFlow()

    override fun onAction(action: NavigationAction) { … }   // Action → Route 매핑 ~40줄
}
```

- **세션 스코프**(`sessionViewModel`)에 선언한다 — 로그아웃 시 `pendingResults`까지 함께 정리된다.
- 이벤트 발화는 `tryEmit`(버퍼 1, `DROP_OLDEST`). ConCafe의 `emit`(replay 0, suspend)은 구독 전 발화 시
  코루틴이 매달리는 문제가 있어 StoryGroup의 기존 규약(`MviViewModel` 주석)을 따른다.

### 4.5 PaneMode.kt — 순수 함수

```kotlin
enum class PaneMode { DETAIL_PANE, FULL_SCREEN, DIALOG }

fun paneModeFor(route: Route): PaneMode = when (route) {
    is Route.GroupDetail, is Route.PostDetail, is Route.ChatRoom -> PaneMode.DETAIL_PANE
    is Route.UserProfile -> PaneMode.DIALOG
    else -> PaneMode.FULL_SCREEN
}
```

현재 최상단 라우트를 읽는 헬퍼도 같은 파일에 둔다 — `NavBackStackEntry.toRoute<T>()`는 타입을 알아야 하므로
`Route` 15종을 순회해 `hasRoute<T>()`로 판별하는 래퍼가 필요하다.

```kotlin
@Composable
fun NavHostController.currentTopRouteAsState(): Route?   // Route.Shell 이면 null 취급
```

**`UserProfile`을 패널이 아니라 다이얼로그로 두는 이유**: 현재 `dialog<UserProfileRoute>`이며
스크림 너머로 뒤 화면이 남는 카드고(iOS `.sheet` 미러), "같은 방 가드"가 `previousBackStackEntry`에 묶여 있다.
넓은 창에서도 중앙 카드가 자연스럽고, 패널로 바꾸면 그 가드가 복잡해진다.

### 4.6 화면이 Action을 올리는 방법

```kotlin
@Composable
fun GroupsScreen(
    …,
    onNavigationAction: (NavigationAction) -> Unit = sessionNavigationViewModel()::onAction
)
```

호출부는 넘기지 않아도 되므로 드릴링이 사라지고, 프리뷰·테스트는 주입할 수 있다.
StoryGroup의 "화면이 default parameter로 VM을 선언" 관용구와 ConCafe의 "화면은 `onNavigationAction`만 안다"를
합친 형태다.

## 5. Compose 배선 (Android + Desktop)

### 5.1 App.kt 재구성

```kotlin
@Composable
private fun SessionContent(themeState: ThemeState, onLogout: () -> Unit) {
    val navigationViewModel = sessionViewModel { NavigationViewModel() }
    val navUiState by navigationViewModel.uiState.collectAsState()
    val navController = rememberNavController()

    LaunchedEffect(Unit) {
        navigationViewModel.event.collect { event ->
            when (event) {
                is NavigationEvent.NavigateTo -> navController.navigate(event.route)
                NavigationEvent.NavigateBack -> navController.popBackStack()
            }
        }
    }
    AppScaffold(navController, navUiState, navigationViewModel::onAction, themeState, onLogout)
}
```

`MainShell` 파라미터는 20개 → `themeState`, `currentTab`, `onNavigationAction`, `onLogout` 4개로 줄어든다.
`homeRefreshRequested`/`onHomeRefreshHandled`/`groupsRefreshRequested`/`onGroupsRefreshHandled` 4개와
`onOpenXxx` 계열 10여 개가 전부 사라진다.

### 5.2 2-pane — `movableContentOf`가 필수다

**이 설계에서 두 번째로 틀리기 쉬운 지점이다.**

좁은 창(오버레이)과 넓은 창(좌우 분할)은 부모 레이아웃이 다르다. `if (twoPane) Row { … } else Box { … }`로
쓰면 창 크기가 breakpoint를 넘나들 때 **NavHost와 셸이 재생성되어 백스택과 스크롤이 날아간다.**

Compose의 `movableContentOf`가 정확히 이 용도다 — 같은 컴포지션 노드를 다른 부모로 옮기면서 상태를 보존한다.

```kotlin
private val TwoPaneBreakpoint = 900.dp

@Composable
private fun AppScaffold(…) {
    val topRoute = navController.currentTopRouteAsState()      // toRoute<>() 안전 래퍼
    val pane = topRoute?.let(::paneModeFor) ?: PaneMode.FULL_SCREEN

    BoxWithConstraints {
        val twoPane = maxWidth >= TwoPaneBreakpoint && pane == PaneMode.DETAIL_PANE

        val shell = remember { movableContentOf { modifier: Modifier -> MainShell(modifier, …) } }
        val host  = remember { movableContentOf { modifier: Modifier -> AppNavHost(modifier, navController, …) } }

        if (twoPane) {
            Row(Modifier.fillMaxSize()) {
                shell(Modifier.weight(1f))
                Box(Modifier.fillMaxHeight().width(1.dp).background(SgTheme.colors.stoneBorder))
                host(Modifier.weight(1f))
            }
        } else {
            Box(Modifier.fillMaxSize()) {
                shell(Modifier.fillMaxSize())
                host(Modifier.fillMaxSize())
            }
        }
    }
}
```

부수 규칙:

- 목적지의 `Surface`(아래 셸로의 터치 전파 차단)는 **오버레이일 때만** 적용한다. 2-pane에선 나란히 있으므로 불필요하다.
  → `AppNavHost`에 `isOverlay: Boolean`을 넘겨 목적지 래퍼에서 분기.
- 2-pane에서 상세를 닫으면 `Route.Shell`로 돌아가 `pane != DETAIL_PANE`이 되고 셸이 자동으로 전체 폭을 차지한다.
  별도의 빈 상태 플레이스홀더는 만들지 않는다.
- `FULL_SCREEN` 목적지(설정·작성·통화·검색 등 11종)는 넓은 창에서도 전체를 덮는다 — 기존 동작 유지.
- 기존 `TabShell`의 600dp NavigationRail 분기는 그대로 둔다. 900dp 이상에서는 레일 + 목록 + 상세 3열이 된다.

### 5.3 삭제되는 것

- `POST_CREATED_KEY` / `GROUP_UPDATED_KEY` 상수와 모든 `savedStateHandle` 읽기/쓰기
- `homeRefreshPending` / `groupsRefreshPending` `remember` 상태와 셸까지 내려가는 4개 파라미터
- `App.kt`에 흩어진 15개 `@Serializable internal` 라우트 선언 (→ `Route.kt`)

## 6. iOS 미러

위치: `iosApp/iosApp/UI/Navigation/`

| 파일 | 내용 |
|---|---|
| `Route.swift` | Kotlin `Route` 미러. `Hashable` 채택(`NavigationStack(path:)` 요구) |
| `NavigationAction.swift` | Kotlin `NavigationAction` 미러 |
| `NavigationEvent.swift` | Kotlin `NavigationEvent` 미러 |
| `NavResult.swift` | Kotlin `NavResult` 미러 |
| `NavigationViewModel.swift` | `MviViewModel` 프로토콜 채택. `@Published uiState`, `let event = PassthroughSubject` |
| `NavigationStackCompat.swift` | ConCafe 것을 포팅 — iOS 16은 `NavigationStack(path:)`, iOS 15는 재귀 `NavigationLink(isActive:)` |

`MainShellView` 변화:

- `@State` 플래그 10개(`showSettings`, `showSearch`, `selectedUserId`, `selectedGroupId`, `showAccountSettings`,
  `showBlockedUsers`, `selectedChatRoom`, `acceptedCall`, `groupsRefreshPending`, `current`) → `@State path: [Route]` 1개
  + `viewModel.uiState.currentTab`
- `navigationDestination(isPresented:)` 7개 + `NavigationLink(isActive:)` 7개 → `NavigationStackCompat`의
  `destination(Route) -> View` switch 하나
- `UserProfile`은 `.sheet` 유지(Compose 다이얼로그 결정과 일치). 후속 push(`ProfileFollowUp`)는
  `onDismiss`에서 `path.append(route)`로 단순화된다 — 기존 패턴은 유지한다(시트가 완전히 닫힌 뒤 push해야 유실되지 않음).
- 2-pane은 iOS에 적용하지 않는다(iPhone 폼팩터). iPad 대응은 범위 밖.

### 6.1 iOS 범위를 2단계로 나눈다

화면 내부에도 자체 `NavigationLink`/`navigationDestination`이 있다 —
`GroupDetailView` 15곳, `SearchView` 10곳, `PostDetailView` 8곳, `ChatRoomView`·`GroupsView`·`FriendsView` 각 3~6곳.

- **1단계(이번 범위)**: 셸 레벨 이관. `MainShellView`의 `@State` 10개를 `path`로 옮기고 계약 파일을 만든다.
  화면 내부 링크는 그대로 둔다 — 동작은 동일하고, 셸 보일러플레이트가 사라진다.
- **2단계(후속)**: 화면 내부 링크를 중앙 `path`로 이관. 리스크가 크고 화면별 회귀 확인이 필요하므로 분리한다.

이 분리를 지키지 않으면 iOS만 한 번에 40곳 이상을 건드리게 되어 Swift 미검증 상태(Mac 없음)에서 위험하다.

## 7. 테스트

`composeApp/src/commonTest/`:

- `NavigationViewModelTest`
  - 각 `NavigationAction` → 기대 `NavigationEvent`/`Route` 매핑 (15종)
  - `SelectTab` → `uiState.currentTab` 갱신
  - `PublishResult` → `pendingResults`에 추가, `ConsumeResult` → 제거
  - `OpenChatRoomFromProfile`: `underlyingChatRoomId`가 같으면 `NavigateBack`, 다르면 `NavigateTo` — 같은 방 가드
- `PaneModeTest`: 15개 라우트 → 기대 `PaneMode`

컴파일·실행 검증:

- Android APK 빌드
- `:composeApp:jvmRun`으로 Desktop 실행 — 창 폭을 900dp 위아래로 늘였다 줄이며 **백스택·스크롤이 유지되는지** 확인
  (`movableContentOf`가 동작하는지 확인하는 핵심 시나리오)
- iOS는 Mac이 없어 컴파일 미검증 — 기존 관행대로 코드 리뷰로 대체하고 사용자가 Xcode에서 확인

## 8. 작업 순서

1. 공용 계약 파일 6종 추가 (`Route`, `NavigationAction`, `NavigationEvent`, `NavResult`, `NavigationViewModel`, `PaneMode`) — 기존 코드 무영향
2. `NavigationViewModelTest` / `PaneModeTest` 작성 (TDD)
3. `App.kt` 재배선 — `AppNavHost` 분리, 셸 파라미터 축소
4. 화면들을 `onNavigationAction` default 파라미터로 전환
5. `savedStateHandle`·`refreshPending` 제거 → `pendingResults` 소비로 교체
6. `movableContentOf` + 2-pane (Desktop)
7. iOS 계약 미러 + `NavigationStackCompat` + `MainShellView` 이관 (1단계)

각 단계마다 Android+jvm 컴파일이 통과해야 다음으로 넘어간다.

## 9. 범위 밖

- iOS 화면 내부 `NavigationLink` 이관 (2단계 후속)
- iPad 2-pane
- 딥링크 / 푸시 알림 → 라우트 복원 (계약이 생기면 쉬워지지만 이번엔 안 한다)
- `AuthFlow`(로그인↔가입)는 세션 밖이라 현행 `when(authScreen)` 유지
