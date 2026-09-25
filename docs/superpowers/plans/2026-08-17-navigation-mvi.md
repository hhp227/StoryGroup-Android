# 네비게이션 MVI 전환 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** StoryGroup의 콜백 프롭 드릴링 네비게이션을 ConCafe식 MVI(NavigationViewModel + Route + NavigationAction + NavigationEvent)로 바꾸고, Desktop에 2-pane을 추가한다.

**Architecture:** `NavigationViewModel`이 탭(`currentTab`)과 화면 간 결과(`pendingResults`)를 상태로 소유하고, 오버레이 이동은 `NavigationEvent` → `NavHostController`에 위임한다. 셸은 지금처럼 NavHost 밖 keep-alive를 유지한다(Paging3 프레젠터 보존). Desktop 2-pane은 `movableContentOf`로 셸/NavHost 컴포지션 노드를 재생성 없이 부모만 바꿔 구현한다.

**Tech Stack:** Kotlin 2.2.20, CMP 1.9.0, jetbrains navigation-compose 2.9.0(멀티플랫폼), kotlinx-coroutines 1.10.2, SwiftUI(iOS 15+)

**Spec:** `docs/superpowers/specs/2026-08-17-navigation-mvi-design.md`

## Global Constraints

- **커밋 금지.** 이 리포는 스테이징까지만 한다 — 커밋·push는 사용자가 직접 한다. 각 태스크의 마지막 스텝은 `git add <경로>`다.
- **`git add -A` 금지.** 워킹트리 CRLF vs 커밋 LF 차이로 무관한 파일이 대량 수정으로 잡힌다. 실제 수정한 파일만 경로로 스테이징한다.
- **CRLF 확인.** 수정 대상 파일이 커밋본에서 CRLF면 수정 후 전체 CRLF로 복원한다. 확인: `file <path>` 출력에 `CRLF` 포함 여부.
- **빌드 명령(WSL → Windows Gradle):**
  ```bash
  cd /mnt/c/Users/hong2/IntelliJIDEAProjects/StoryGroup/StoryGroup-Android && \
  cmd.exe /c "set JAVA_HOME=C:\Program Files\ojdkbuild\java-17-openjdk-17.0.3.0.6-1&& cd /d C:\Users\hong2\IntelliJIDEAProjects\StoryGroup\StoryGroup-Android&& gradlew.bat <task>"
  ```
- **검증 태스크:** 단위 테스트 `:composeApp:jvmTest`, Android 컴파일 `:composeApp:assembleDebug`, Desktop 실행 `:composeApp:run`
- **iOS는 Mac이 없어 컴파일 미검증.** Swift는 코드 리뷰로 대체하고 사용자가 Xcode에서 확인한다.
- **패키지:** `kr.hhp227.storygroup`. 새 네비게이션 코드는 `kr.hhp227.storygroup.ui.navigation`.
- **주석은 한국어**, 기존 코드 스타일(왜 그런지 설명하는 주석)을 따른다.
- **이벤트 발화는 `tryEmit`** (버퍼 1, `DROP_OLDEST`) — 기존 `MviViewModel` 규약.

---

## File Structure

**신규 (Compose, `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/navigation/`)**

| 파일 | 책임 |
|---|---|
| `MainDestination.kt` | 탭 enum (기존 `ui/shell/MainShell.kt`에서 이동) |
| `Route.kt` | 목적지 15종 `@Serializable sealed interface` |
| `NavResult.kt` | 화면 간 결과 신호 4종 |
| `NavigationAction.kt` | 화면이 올리는 의도 |
| `NavigationEvent.kt` | VM이 방출하는 이동 지시 2종 |
| `NavigationViewModel.kt` | 탭·결과 상태 소유 + Action→Event 매핑 |
| `PaneMode.kt` | `paneModeFor(route)` 순수 함수 |
| `NavigationLocals.kt` | `sessionNavigationViewModel()`, `currentTopRouteAsState()` |
| `AppNavHost.kt` | NavHost 목적지 배선 (App.kt에서 분리) |

**신규 (테스트, `composeApp/src/commonTest/kotlin/kr/hhp227/storygroup/ui/navigation/`)**

| 파일 | 책임 |
|---|---|
| `PaneModeTest.kt` | 라우트 15종 → PaneMode |
| `NavigationViewModelTest.kt` | Action→Event 매핑, 탭 상태, pendingResults, 같은 방 가드 |

**신규 (iOS, `iosApp/iosApp/UI/Navigation/`)**

`Route.swift`, `NavResult.swift`, `NavigationAction.swift`, `NavigationEvent.swift`, `NavigationViewModel.swift`, `NavigationStackCompat.swift`

**수정**

`App.kt`(520→~130줄), `ui/shell/{MainShell,TabShell,DrawerShell}.kt`, 화면 18종, `gradle/libs.versions.toml`, `composeApp/build.gradle.kts`, iOS `MainShellView.swift`/`TabShellView.swift`/`DrawerShellView.swift`, `iosApp.xcodeproj/project.pbxproj`

---

## Task 1: Route + PaneMode

**Files:**
- Create: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/navigation/Route.kt`
- Create: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/navigation/PaneMode.kt`
- Test: `composeApp/src/commonTest/kotlin/kr/hhp227/storygroup/ui/navigation/PaneModeTest.kt`

**Interfaces:**
- Consumes: 없음 (첫 태스크)
- Produces: `Route` sealed interface 15종, `enum class PaneMode { DETAIL_PANE, FULL_SCREEN, DIALOG }`, `fun paneModeFor(route: Route): PaneMode`

- [ ] **Step 1: 실패하는 테스트 작성**

`composeApp/src/commonTest/kotlin/kr/hhp227/storygroup/ui/navigation/PaneModeTest.kt`:

```kotlin
package kr.hhp227.storygroup.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class PaneModeTest {

    /** 목록↔상세 성격만 우측 패널 — 좌우 문맥이 실제로 이어지는 목적지들 */
    @Test
    fun detailRoutesUsePane() {
        assertEquals(PaneMode.DETAIL_PANE, paneModeFor(Route.GroupDetail(1L)))
        assertEquals(PaneMode.DETAIL_PANE, paneModeFor(Route.PostDetail(1L, 2L)))
        assertEquals(PaneMode.DETAIL_PANE, paneModeFor(Route.ChatRoom(1L, null, "방")))
    }

    /**
     * 공개 프로필은 카드 다이얼로그로 남는다 — 스크림 너머로 뒤 화면이 보이고,
     * 같은 방 가드가 백스택 바로 아래 엔트리에 묶여 있어 패널로 바꾸면 깨진다
     */
    @Test
    fun userProfileStaysDialog() {
        assertEquals(PaneMode.DIALOG, paneModeFor(Route.UserProfile(7L)))
    }

    /** 작업에 몰입하는 화면은 넓은 창에서도 전체를 덮는다 */
    @Test
    fun taskRoutesUseFullScreen() {
        val fullScreenRoutes = listOf(
            Route.Shell,
            Route.CreatePost(null),
            Route.GroupEdit(1L),
            Route.GroupReports(1L),
            Route.Call(1L, "방", ring = true),
            Route.AccountSettings,
            Route.AppSettings,
            Route.BlockedUsers,
            Route.CreateGroup,
            Route.DiscoverGroups,
            Route.Search
        )

        fullScreenRoutes.forEach { route ->
            assertEquals(PaneMode.FULL_SCREEN, paneModeFor(route), "route=$route")
        }
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run:
```bash
cd /mnt/c/Users/hong2/IntelliJIDEAProjects/StoryGroup/StoryGroup-Android && \
cmd.exe /c "set JAVA_HOME=C:\Program Files\ojdkbuild\java-17-openjdk-17.0.3.0.6-1&& cd /d C:\Users\hong2\IntelliJIDEAProjects\StoryGroup\StoryGroup-Android&& gradlew.bat :composeApp:jvmTest"
```
Expected: 컴파일 실패 — `Unresolved reference: Route`, `Unresolved reference: PaneMode`

- [ ] **Step 3: Route.kt 작성**

```kotlin
package kr.hhp227.storygroup.ui.navigation

import kotlinx.serialization.Serializable

/**
 * 목적지 — 기존 App.kt에 흩어져 있던 15종을 한곳으로 모았다.
 * ⚠️ private/internal이면 안 된다: Desktop(JVM)에서 kotlinx.serialization 리플렉션이
 * 패키지 전용 클래스에 접근하지 못해 시작 즉시 IllegalAccessException으로 죽는다(Android는 통과).
 * 화면들이 ui.navigation 밖에서 참조하므로 public이 맞다.
 * iosApp UI/Navigation/Route.swift와 1:1 미러.
 */
@Serializable
sealed interface Route {

    /** NavHost 시작 목적지 — 아무것도 그리지 않는 빈 오버레이. 셸은 NavHost 밖에서 항상 살아있다 */
    @Serializable
    data object Shell : Route

    @Serializable
    data class GroupDetail(val groupId: Long) : Route

    /**
     * 게시글 상세 — 라운지 글도 라운지 그룹 id로 들어오므로 홈·그룹 피드가 같은 목적지를 쓴다
     */
    @Serializable
    data class PostDetail(val groupId: Long, val postId: Long) : Route

    /** 채팅방 — 그룹 채팅(groupId 있음)/DM(null) 공용. title은 허브가 아는 표시명 */
    @Serializable
    data class ChatRoom(val chatRoomId: Long, val groupId: Long?, val title: String) : Route

    /** 공개 프로필 — 카드 다이얼로그(iOS 시트 미러) */
    @Serializable
    data class UserProfile(val userId: Long) : Route

    /**
     * 게시글 작성 — groupId null이면 라운지(홈 피드)에 게시.
     * postId가 있으면 같은 폼이 수정 모드로 동작한다
     */
    @Serializable
    data class CreatePost(val groupId: Long?, val postId: Long? = null) : Route

    @Serializable
    data class GroupEdit(val groupId: Long) : Route

    /** 그룹 신고함(모더레이터) */
    @Serializable
    data class GroupReports(val groupId: Long) : Route

    /**
     * 방 통화 — DM 1:1·그룹 방 공용.
     * ring=true는 발신(입장+벨울림), false는 수신 배너 수락으로 진입.
     * video=false면 보이스톡(카메라 OFF·수화구 시작)
     */
    @Serializable
    data class Call(
        val chatRoomId: Long,
        val title: String,
        val ring: Boolean,
        val video: Boolean = true
    ) : Route

    @Serializable
    data object AccountSettings : Route

    @Serializable
    data object AppSettings : Route

    @Serializable
    data object BlockedUsers : Route

    @Serializable
    data object CreateGroup : Route

    @Serializable
    data object DiscoverGroups : Route

    @Serializable
    data object Search : Route
}
```

- [ ] **Step 4: PaneMode.kt 작성**

```kotlin
package kr.hhp227.storygroup.ui.navigation

/**
 * 목적지가 화면을 어떻게 차지하는가 — Desktop 2-pane 판정에 쓴다.
 * 순수 함수라 3플랫폼이 같은 규칙을 공유하고 단위 테스트가 가능하다.
 */
enum class PaneMode {
    /** 넓은 창(≥900dp)에서 좌측 셸과 나란히 우측 패널 */
    DETAIL_PANE,

    /** 창 폭과 무관하게 전체를 덮는다 */
    FULL_SCREEN,

    /** 스크림 위 카드 — 뒤 화면이 남는다 */
    DIALOG
}

/**
 * 목록↔상세로 문맥이 이어지는 목적지만 패널이다.
 * 작성·설정·통화처럼 작업에 몰입하는 화면은 넓은 창에서도 전체를 덮는 편이 낫다.
 */
fun paneModeFor(route: Route): PaneMode = when (route) {
    is Route.GroupDetail, is Route.PostDetail, is Route.ChatRoom -> PaneMode.DETAIL_PANE
    is Route.UserProfile -> PaneMode.DIALOG
    Route.Shell,
    is Route.CreatePost,
    is Route.GroupEdit,
    is Route.GroupReports,
    is Route.Call,
    Route.AccountSettings,
    Route.AppSettings,
    Route.BlockedUsers,
    Route.CreateGroup,
    Route.DiscoverGroups,
    Route.Search -> PaneMode.FULL_SCREEN
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `gradlew.bat :composeApp:jvmTest` (Global Constraints의 전체 명령 사용)
Expected: PASS — `PaneModeTest` 3개 통과

- [ ] **Step 6: 스테이징**

```bash
cd /mnt/c/Users/hong2/IntelliJIDEAProjects/StoryGroup/StoryGroup-Android
git add composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/navigation/Route.kt \
        composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/navigation/PaneMode.kt \
        composeApp/src/commonTest/kotlin/kr/hhp227/storygroup/ui/navigation/PaneModeTest.kt
```

---

## Task 2: MainDestination 이동

`NavigationAction.SelectTab`이 `MainDestination`을 참조하므로 먼저 옮긴다. 그대로 두면 `navigation → shell` 역방향 의존이 생긴다.

**Files:**
- Create: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/navigation/MainDestination.kt`
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/shell/MainShell.kt` (enum 선언 삭제 + import 추가)
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/shell/TabShell.kt` (import 추가)
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/shell/DrawerShell.kt` (import 추가)

**Interfaces:**
- Consumes: 없음
- Produces: `kr.hhp227.storygroup.ui.navigation.MainDestination` — `enum class MainDestination(val label: String, val icon: ImageVector, val inTabs: Boolean)`, 값 `HOME, GROUPS, FRIENDS, CHAT, NOTIFICATIONS, PROFILE`

- [ ] **Step 1: MainDestination.kt 생성**

`MainShell.kt` 상단의 enum 선언을 그대로 옮긴다(주석 포함):

```kotlin
package kr.hhp227.storygroup.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 내비 목적지 — 탭/레일은 5개(홈·그룹·친구·채팅·프로필), 알림은 상단바 종 아이콘으로 진입
 * (레거시 드로어는 전부 노출). NavigationAction.SelectTab이 참조하므로 ui.navigation 소속이다.
 */
enum class MainDestination(val label: String, val icon: ImageVector, val inTabs: Boolean) {
    HOME("홈", Icons.Default.Home, true),
    GROUPS("그룹", Icons.Default.Groups, true),
    FRIENDS("친구", Icons.Default.People, true),
    CHAT("채팅", Icons.AutoMirrored.Filled.Chat, true),
    NOTIFICATIONS("알림", Icons.Default.Notifications, false),
    PROFILE("프로필", Icons.Default.Person, true)
}
```

- [ ] **Step 2: MainShell.kt에서 enum 선언 삭제하고 import로 교체**

`MainShell.kt`에서 `enum class MainDestination(...) { ... }` 블록 전체를 삭제하고, import 목록에 추가:

```kotlin
import kr.hhp227.storygroup.ui.navigation.MainDestination
```

사용하지 않게 된 아이콘 import(`Icons.Default.Home` 등)가 남으면 삭제한다.

- [ ] **Step 3: TabShell.kt / DrawerShell.kt에 import 추가**

두 파일 모두 import 목록에 추가:

```kotlin
import kr.hhp227.storygroup.ui.navigation.MainDestination
```

- [ ] **Step 4: 컴파일 확인**

Run: `gradlew.bat :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL (동작 변화 없음 — 순수 이동)

- [ ] **Step 5: 스테이징**

```bash
git add composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/navigation/MainDestination.kt \
        composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/shell/MainShell.kt \
        composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/shell/TabShell.kt \
        composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/shell/DrawerShell.kt
```

---

## Task 3: NavResult + Action + Event + NavigationViewModel

**Files:**
- Modify: `gradle/libs.versions.toml` (coroutines-test 추가)
- Modify: `composeApp/build.gradle.kts` (commonTest 의존성)
- Create: `.../ui/navigation/NavResult.kt`
- Create: `.../ui/navigation/NavigationAction.kt`
- Create: `.../ui/navigation/NavigationEvent.kt`
- Create: `.../ui/navigation/NavigationViewModel.kt`
- Test: `composeApp/src/commonTest/kotlin/kr/hhp227/storygroup/ui/navigation/NavigationViewModelTest.kt`

**Interfaces:**
- Consumes: Task 1의 `Route`, Task 2의 `MainDestination`
- Produces:
  - `sealed interface NavResult` — `PostCreated(groupId: Long?)`, `PostUpdated(groupId: Long, postId: Long)`, `GroupUpdated(groupId: Long)`, `GroupsChanged`
  - `sealed interface NavigationAction` — 아래 Step 4 목록
  - `sealed interface NavigationEvent` — `NavigateTo(route: Route)`, `NavigateBack`
  - `class NavigationViewModel : ViewModel(), MviViewModel<NavigationViewModel.UiState, NavigationAction, NavigationEvent>` with `data class UiState(currentTab: MainDestination = HOME, pendingResults: Set<NavResult> = emptySet())`

- [ ] **Step 1: 테스트 의존성 추가**

`gradle/libs.versions.toml`의 `[libraries]`에 추가:

```toml
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinx-coroutines" }
```

`composeApp/build.gradle.kts`의 `commonTest.dependencies`를 다음으로 교체:

```kotlin
commonTest.dependencies {
    implementation(libs.kotlin.test)
    // NavigationViewModel의 event(SharedFlow, replay 0)는 구독 중에만 전달되므로
    // runTest + backgroundScope로 수집기를 띄워야 검증할 수 있다
    implementation(libs.kotlinx.coroutines.test)
}
```

- [ ] **Step 2: 실패하는 테스트 작성**

`composeApp/src/commonTest/kotlin/kr/hhp227/storygroup/ui/navigation/NavigationViewModelTest.kt`:

```kotlin
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
            listOf(
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
            listOf(
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
     * 빠져 있어 이벤트를 받을 수 없다. 돌아와서 읽어갈 때까지 남아야 한다
     */
    @Test
    fun resultsStayPendingUntilConsumed() = runTest {
        val viewModel = NavigationViewModel()
        val result = NavResult.PostCreated(groupId = 4L)

        viewModel.onAction(NavigationAction.PublishResult(result))
        assertEquals(setOf(result), viewModel.uiState.value.pendingResults)

        viewModel.onAction(NavigationAction.ConsumeResult(result))
        assertEquals(emptySet(), viewModel.uiState.value.pendingResults)
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

        assertEquals(listOf(NavigationEvent.NavigateBack), events)
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

        assertEquals(listOf(NavigationEvent.NavigateTo(Route.ChatRoom(11L, null, "상대"))), events)
    }
}
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `gradlew.bat :composeApp:jvmTest`
Expected: 컴파일 실패 — `Unresolved reference: NavigationViewModel`, `NavigationAction`, `NavResult`, `NavigationEvent`

- [ ] **Step 4: NavResult.kt / NavigationAction.kt / NavigationEvent.kt 작성**

`NavResult.kt`:

```kotlin
package kr.hhp227.storygroup.ui.navigation

/**
 * 화면 간 결과 신호 — 기존 savedStateHandle 키(POST_CREATED_KEY·GROUP_UPDATED_KEY)와
 * 셸 플래그(homeRefreshPending·groupsRefreshPending)를 대체한다.
 * ⚠️ 이벤트가 아니라 상태로 다뤄야 한다: 결과를 낸 화면이 떠 있는 동안 받을 화면은
 * NavHost 백스택에만 있고 컴포지션에서 빠져 있어 이벤트를 놓친다.
 */
sealed interface NavResult {

    /** groupId == null 이면 라운지(홈 피드)에 올린 글 */
    data class PostCreated(val groupId: Long?) : NavResult

    data class PostUpdated(val groupId: Long, val postId: Long) : NavResult

    data class GroupUpdated(val groupId: Long) : NavResult

    /** 나가기·삭제·수정으로 내 그룹 목록이 바뀜 */
    data object GroupsChanged : NavResult
}
```

`NavigationAction.kt`:

```kotlin
package kr.hhp227.storygroup.ui.navigation

/**
 * 화면이 올리는 의도 — 화면은 Route를 몰라도 된다(ConCafe 패턴).
 * iosApp UI/Navigation/NavigationAction.swift와 1:1 미러.
 */
sealed interface NavigationAction {

    data class NavigateToGroupDetail(val groupId: Long) : NavigationAction

    data class NavigateToPostDetail(val groupId: Long, val postId: Long) : NavigationAction

    data class NavigateToChatRoom(
        val chatRoomId: Long,
        val groupId: Long?,
        val title: String
    ) : NavigationAction

    data class NavigateToUserProfile(val userId: Long) : NavigationAction

    data class NavigateToCreatePost(val groupId: Long?, val postId: Long? = null) : NavigationAction

    data class NavigateToGroupEdit(val groupId: Long) : NavigationAction

    data class NavigateToGroupReports(val groupId: Long) : NavigationAction

    /** 발신 — 입장+벨울림(그룹 방은 서버가 방 멤버 전원 팬아웃) */
    data class StartCall(val chatRoomId: Long, val title: String, val video: Boolean) : NavigationAction

    /** 수신 배너 수락 — 입장만(구독=입장), 벨은 다시 울리지 않는다 */
    data class AcceptIncomingCall(
        val chatRoomId: Long,
        val title: String,
        val video: Boolean
    ) : NavigationAction

    data object NavigateToAccountSettings : NavigationAction

    data object NavigateToAppSettings : NavigationAction

    data object NavigateToBlockedUsers : NavigationAction

    data object NavigateToCreateGroup : NavigationAction

    data object NavigateToDiscoverGroups : NavigationAction

    data object NavigateToSearch : NavigationAction

    data object NavigateBack : NavigationAction

    data class SelectTab(val destination: MainDestination) : NavigationAction

    /**
     * 프로필 다이얼로그의 DM 버튼 — 바로 아래가 같은 방이면 방을 또 쌓지 않고 닫기만 한다
     * (카카오톡 방식. DM 열기는 멱등이라 id 비교가 정확하다)
     */
    data class OpenChatRoomFromProfile(
        val chatRoomId: Long,
        val groupId: Long?,
        val title: String,
        val underlyingChatRoomId: Long?
    ) : NavigationAction

    data class PublishResult(val result: NavResult) : NavigationAction

    data class ConsumeResult(val result: NavResult) : NavigationAction
}
```

`NavigationEvent.kt`:

```kotlin
package kr.hhp227.storygroup.ui.navigation

/**
 * VM → 호스트 이동 지시. 둘이면 충분하다 —
 * 다른 방으로 갈 때 프로필 다이얼로그는 Navigation이 알아서 pop한다.
 * 탭은 이벤트가 아니라 UiState.currentTab이다.
 */
sealed interface NavigationEvent {
    data class NavigateTo(val route: Route) : NavigationEvent
    data object NavigateBack : NavigationEvent
}
```

- [ ] **Step 5: NavigationViewModel.kt 작성**

```kotlin
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
 * 세션 스코프에 선언한다 — 로그아웃 시 pendingResults까지 함께 정리된다.
 * iosApp UI/Navigation/NavigationViewModel.swift와 1:1 미러.
 */
class NavigationViewModel : ViewModel(),
    MviViewModel<NavigationViewModel.UiState, NavigationAction, NavigationEvent> {

    data class UiState(
        val currentTab: MainDestination = MainDestination.HOME,
        /** 화면이 재진입해 읽어갈 때까지 남는다 — savedStateHandle 키의 일반화 */
        val pendingResults: Set<NavResult> = emptySet()
    )

    private val _uiState = MutableStateFlow(UiState())
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<NavigationEvent>(
        extraBufferCapacity = 1,
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
            NavigationAction.NavigateToSearch -> emit(navigateTo(Route.Search))
        }
    }

    private fun navigateTo(route: Route): NavigationEvent = NavigationEvent.NavigateTo(route)

    /** tryEmit — 구독 전 발화에도 코루틴이 매달리지 않는다(MviViewModel 규약) */
    private fun emit(event: NavigationEvent) {
        _event.tryEmit(event)
    }
}
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `gradlew.bat :composeApp:jvmTest`
Expected: PASS — `NavigationViewModelTest` 6개 + `PaneModeTest` 3개 통과

- [ ] **Step 7: 스테이징**

```bash
git add gradle/libs.versions.toml composeApp/build.gradle.kts \
        composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/navigation/NavResult.kt \
        composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/navigation/NavigationAction.kt \
        composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/navigation/NavigationEvent.kt \
        composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/navigation/NavigationViewModel.kt \
        composeApp/src/commonTest/kotlin/kr/hhp227/storygroup/ui/navigation/NavigationViewModelTest.kt
```

---

## Task 4: 헬퍼 (sessionNavigationViewModel, currentTopRouteAsState)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/navigation/NavigationLocals.kt`

**Interfaces:**
- Consumes: Task 1의 `Route`, Task 3의 `NavigationViewModel`, 기존 `kr.hhp227.storygroup.di.sessionViewModel`
- Produces:
  - `@Composable fun sessionNavigationViewModel(): NavigationViewModel`
  - `@Composable fun NavHostController.currentTopRouteAsState(): Route?` — `Route.Shell`이면 null

- [ ] **Step 1: NavigationLocals.kt 작성**

```kotlin
package kr.hhp227.storygroup.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import kr.hhp227.storygroup.di.sessionViewModel

/**
 * 세션 스코프의 단일 NavigationViewModel — 화면들이 default parameter로 이걸 조회해
 * onNavigationAction을 얻는다(호출부가 람다를 내려줄 필요가 없다).
 */
@Composable
fun sessionNavigationViewModel(): NavigationViewModel = sessionViewModel { NavigationViewModel() }

/**
 * 현재 최상단 목적지 — 2-pane 판정에 쓴다. 셸만 보이는 상태(Route.Shell)면 null.
 * NavBackStackEntry.toRoute<T>()는 타입을 알아야 하므로 hasRoute로 판별해 분기한다.
 */
@Composable
fun NavHostController.currentTopRouteAsState(): Route? {
    val entry by currentBackStackEntryAsState()

    return entry?.toRouteOrNull()
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
```

`hasRoute`는 `androidx.navigation.NavDestination.hasRoute` 확장이다. import가 자동으로 안 잡히면 `import androidx.navigation.NavDestination.Companion.hasRoute`를 추가한다(기존 `App.kt`가 `UserProfileRoute` 분기에서 같은 함수를 쓰고 있으니 그 import를 그대로 따른다).

- [ ] **Step 2: 컴파일 확인**

Run: `gradlew.bat :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL (아직 아무도 안 쓰므로 동작 변화 없음)

- [ ] **Step 3: 스테이징**

```bash
git add composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/navigation/NavigationLocals.kt
```

---

## Task 5: AppNavHost 분리 + App.kt 재배선

가장 큰 태스크다. 화면 시그니처는 **건드리지 않는다** — 셸과 AppNavHost가 `onNavigationAction`을 화면의 기존 콜백으로 변환한다. 화면 전환은 Task 7·8에서 한다.

**Files:**
- Create: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/navigation/AppNavHost.kt`
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/App.kt` (520 → ~130줄)
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/shell/MainShell.kt`
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/shell/TabShell.kt`
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/shell/DrawerShell.kt`

**Interfaces:**
- Consumes: Task 1~4 전부
- Produces:
  - `@Composable fun AppNavHost(navController: NavHostController, onNavigationAction: (NavigationAction) -> Unit, pendingResults: Set<NavResult>, themeState: ThemeState, isOverlay: Boolean, modifier: Modifier)`
  - `MainShell(themeState, currentTab, pendingResults, onNavigationAction, onLogout, modifier)` — 파라미터 20개 → 6개
  - `TabShell`/`DrawerShell`/`DestinationContent`도 같은 6개
  - ⚠️ `pendingResults`는 이 태스크에서만 셸을 통과한다. Task 6에서 화면이 직접 읽게 되면 셸에서 빠져 5개가 된다.

- [ ] **Step 1: AppNavHost.kt 작성**

`App.kt`의 `NavHost { … }` 블록을 그대로 옮기되, `navController.navigate(XxxRoute(...))`를 `onNavigationAction(NavigationAction.NavigateToXxx(...))`로, `navController.popBackStack()`을 `onNavigationAction(NavigationAction.NavigateBack)`으로 바꾼다.

```kotlin
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
 * @param isOverlay 좁은 창(셸 위를 덮는 모드)이면 true. 2-pane에선 나란히 서므로 false —
 *   아래 셸로의 터치 전파를 막는 Surface가 필요 없다.
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    onNavigationAction: (NavigationAction) -> Unit,
    pendingResults: Set<NavResult>,
    themeState: ThemeState,
    isOverlay: Boolean,
    modifier: Modifier = Modifier
) {
    NavHost(navController = navController, startDestination = Route.Shell, modifier = modifier) {
        composable<Route.Shell> {}

        composable<Route.GroupDetail> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.GroupDetail>()
            val postCreated = NavResult.PostCreated(route.groupId) in pendingResults
            val groupUpdated = NavResult.GroupUpdated(route.groupId) in pendingResults

            OverlayContainer(isOverlay) {
                GroupDetailScreen(
                    groupId = route.groupId,
                    onBack = { onNavigationAction(NavigationAction.NavigateBack) },
                    onCreatePost = { onNavigationAction(NavigationAction.NavigateToCreatePost(route.groupId)) },
                    onOpenPostDetail = { postId ->
                        onNavigationAction(NavigationAction.NavigateToPostDetail(route.groupId, postId))
                    },
                    onOpenChatRoom = { chatRoomId, groupId, title ->
                        onNavigationAction(NavigationAction.NavigateToChatRoom(chatRoomId, groupId, title))
                    },
                    refreshRequested = postCreated,
                    onRefreshHandled = {
                        onNavigationAction(NavigationAction.ConsumeResult(NavResult.PostCreated(route.groupId)))
                    },
                    onGroupClosed = {
                        onNavigationAction(NavigationAction.PublishResult(NavResult.GroupsChanged))
                        onNavigationAction(NavigationAction.NavigateBack)
                    },
                    groupUpdateRequested = groupUpdated,
                    onGroupUpdateHandled = {
                        onNavigationAction(NavigationAction.ConsumeResult(NavResult.GroupUpdated(route.groupId)))
                    },
                    onOpenGroupEdit = { onNavigationAction(NavigationAction.NavigateToGroupEdit(route.groupId)) },
                    onOpenAccountSettings = { onNavigationAction(NavigationAction.NavigateToAccountSettings) },
                    onOpenAppSettings = { onNavigationAction(NavigationAction.NavigateToAppSettings) },
                    onOpenGroupReports = { onNavigationAction(NavigationAction.NavigateToGroupReports(route.groupId)) },
                    onOpenUserProfile = { userId ->
                        onNavigationAction(NavigationAction.NavigateToUserProfile(userId))
                    },
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                )
            }
        }

        composable<Route.ChatRoom> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.ChatRoom>()

            OverlayContainer(isOverlay) {
                ChatRoomScreen(
                    chatRoomId = route.chatRoomId,
                    groupId = route.groupId,
                    title = route.title,
                    onBack = { onNavigationAction(NavigationAction.NavigateBack) },
                    onStartCall = { video ->
                        onNavigationAction(NavigationAction.StartCall(route.chatRoomId, route.title, video))
                    },
                    onOpenUserProfile = { userId ->
                        onNavigationAction(NavigationAction.NavigateToUserProfile(userId))
                    },
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                )
            }
        }

        composable<Route.PostDetail> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.PostDetail>()
            val postUpdated = NavResult.PostUpdated(route.groupId, route.postId) in pendingResults

            OverlayContainer(isOverlay) {
                PostDetailScreen(
                    groupId = route.groupId,
                    postId = route.postId,
                    onBack = { onNavigationAction(NavigationAction.NavigateBack) },
                    onEdit = {
                        onNavigationAction(NavigationAction.NavigateToCreatePost(route.groupId, route.postId))
                    },
                    onOpenUserProfile = { userId ->
                        onNavigationAction(NavigationAction.NavigateToUserProfile(userId))
                    },
                    refreshRequested = postUpdated,
                    onRefreshHandled = {
                        onNavigationAction(
                            NavigationAction.ConsumeResult(NavResult.PostUpdated(route.groupId, route.postId))
                        )
                    },
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                )
            }
        }

        composable<Route.CreatePost> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.CreatePost>()

            OverlayContainer(isOverlay) {
                CreatePostScreen(
                    groupId = route.groupId,
                    postId = route.postId,
                    onBack = { onNavigationAction(NavigationAction.NavigateBack) },
                    onCreated = {
                        // 수정이면 상세가, 신규면 피드가 읽어간다
                        val result = if (route.postId != null && route.groupId != null) {
                            NavResult.PostUpdated(route.groupId, route.postId)
                        } else {
                            NavResult.PostCreated(route.groupId)
                        }

                        onNavigationAction(NavigationAction.PublishResult(result))
                        onNavigationAction(NavigationAction.NavigateBack)
                    },
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                )
            }
        }

        composable<Route.Call> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.Call>()

            OverlayContainer(isOverlay) {
                CallScreen(
                    chatRoomId = route.chatRoomId,
                    title = route.title,
                    ring = route.ring,
                    video = route.video,
                    onBack = { onNavigationAction(NavigationAction.NavigateBack) },
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                )
            }
        }

        composable<Route.GroupEdit> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.GroupEdit>()

            OverlayContainer(isOverlay) {
                GroupEditScreen(
                    groupId = route.groupId,
                    onBack = { onNavigationAction(NavigationAction.NavigateBack) },
                    onSaved = {
                        // 상세는 커버·제목을, 목록은 카드를 다시 읽는다
                        onNavigationAction(NavigationAction.PublishResult(NavResult.GroupUpdated(route.groupId)))
                        onNavigationAction(NavigationAction.PublishResult(NavResult.GroupsChanged))
                        onNavigationAction(NavigationAction.NavigateBack)
                    },
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                )
            }
        }

        composable<Route.GroupReports> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.GroupReports>()

            OverlayContainer(isOverlay) {
                GroupReportsScreen(
                    groupId = route.groupId,
                    onBack = { onNavigationAction(NavigationAction.NavigateBack) },
                    onOpenPostDetail = { postId ->
                        onNavigationAction(NavigationAction.NavigateToPostDetail(route.groupId, postId))
                    },
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                )
            }
        }

        composable<Route.AccountSettings> {
            OverlayContainer(isOverlay) {
                AccountSettingsScreen(
                    onBack = { onNavigationAction(NavigationAction.NavigateBack) },
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                )
            }
        }

        composable<Route.BlockedUsers> {
            OverlayContainer(isOverlay) {
                BlockedUsersScreen(
                    onBack = { onNavigationAction(NavigationAction.NavigateBack) },
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                )
            }
        }

        composable<Route.AppSettings> {
            OverlayContainer(isOverlay) {
                AppSettingsScreen(
                    themeState = themeState,
                    onBack = { onNavigationAction(NavigationAction.NavigateBack) }
                )
            }
        }

        composable<Route.CreateGroup> {
            OverlayContainer(isOverlay) {
                CreateGroupScreen(
                    onBack = { onNavigationAction(NavigationAction.NavigateBack) },
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                )
            }
        }

        composable<Route.DiscoverGroups> {
            OverlayContainer(isOverlay) {
                DiscoverGroupsScreen(
                    onBack = { onNavigationAction(NavigationAction.NavigateBack) },
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                )
            }
        }

        composable<Route.Search> {
            OverlayContainer(isOverlay) {
                SearchScreen(
                    onBack = { onNavigationAction(NavigationAction.NavigateBack) },
                    onOpenGroupDetail = { groupId ->
                        onNavigationAction(NavigationAction.NavigateToGroupDetail(groupId))
                    },
                    onOpenPostDetail = { groupId, postId ->
                        onNavigationAction(NavigationAction.NavigateToPostDetail(groupId, postId))
                    },
                    onOpenChatRoom = { chatRoomId, groupId, title ->
                        onNavigationAction(NavigationAction.NavigateToChatRoom(chatRoomId, groupId, title))
                    },
                    onOpenUserProfile = { userId ->
                        onNavigationAction(NavigationAction.NavigateToUserProfile(userId))
                    },
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                )
            }
        }

        // 풀스크린이 아니라 카드 다이얼로그 — 뒤 화면이 스크림 너머로 남는다(iOS 시트 미러)
        dialog<Route.UserProfile>(
            dialogProperties = DialogProperties(usePlatformDefaultWidth = false)
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<Route.UserProfile>()
            // 바로 아래가 채팅방이면 그 방 id — 같은 방으로 가려 할 때 또 쌓지 않기 위한 판정 재료
            val underlyingChatRoomId = navController.previousBackStackEntry
                ?.takeIf { it.destination.hasRoute<Route.ChatRoom>() }
                ?.toRoute<Route.ChatRoom>()
                ?.chatRoomId

            UserProfileScreen(
                userId = route.userId,
                onClose = { onNavigationAction(NavigationAction.NavigateBack) },
                onOpenChatRoom = { chatRoomId, groupId, title ->
                    onNavigationAction(
                        NavigationAction.OpenChatRoomFromProfile(
                            chatRoomId = chatRoomId,
                            groupId = groupId,
                            title = title,
                            underlyingChatRoomId = underlyingChatRoomId
                        )
                    )
                },
                onOpenAccountSettings = { onNavigationAction(NavigationAction.NavigateToAccountSettings) },
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(0.92f)
            )
        }
    }
}

/**
 * 좁은 창에선 Surface가 아래 셸로의 터치 전파를 막는다.
 * 2-pane에선 나란히 서므로 감싸지 않는다(감싸면 우측 패널이 불필요한 배경 레이어를 얻는다).
 */
@Composable
private fun OverlayContainer(isOverlay: Boolean, content: @Composable () -> Unit) {
    if (isOverlay) {
        Surface(color = SgTheme.colors.paper) { content() }
    } else {
        content()
    }
}
```

`hasRoute` import는 Task 4와 동일하게 기존 `App.kt`가 쓰던 것을 따른다.

- [ ] **Step 2: App.kt 재작성**

`App.kt`에서 라우트 15종 선언, `POST_CREATED_KEY`/`GROUP_UPDATED_KEY` 상수, `NavHost` 블록을 전부 삭제하고 아래로 교체한다. `App()`과 `AuthFlow()`는 그대로 둔다.

```kotlin
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
        Box {
            // 2-pane은 Task 9에서 붙인다 — 지금은 기존과 같은 오버레이 배치
            MainShell(
                themeState = themeState,
                currentTab = navUiState.currentTab,
                onNavigationAction = onNavigationAction,
                onLogout = onLogout
            )
            AppNavHost(
                navController = navController,
                onNavigationAction = onNavigationAction,
                pendingResults = navUiState.pendingResults,
                themeState = themeState,
                isOverlay = true
            )
            // 수신 통화 배너 — 어떤 화면 위에서든 뜬다(Box의 마지막 자식 = 최상단)
            incomingCallUiState.incomingCall?.let { call ->
                IncomingCallBanner(
                    call = call,
                    onAccept = {
                        incomingCallViewModel.onAction(IncomingCallViewModel.Action.Dismiss)
                        onNavigationAction(
                            NavigationAction.AcceptIncomingCall(
                                chatRoomId = call.chatRoomId,
                                title = call.roomName ?: call.callerName,
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
```

- [ ] **Step 3: MainShell 파라미터 축소**

`MainShell.kt`의 시그니처를 교체하고, 내부에서 `currentDestination`을 자체 `rememberSaveable` 대신 파라미터로 받는다. `showSettings` 로컬 상태는 삭제하고 `NavigationAction.NavigateToAppSettings`로 넘긴다(AppSettings는 이제 NavHost 목적지다).

```kotlin
@Composable
fun MainShell(
    themeState: ThemeState,
    currentTab: MainDestination,
    pendingResults: Set<NavResult>,
    onNavigationAction: (NavigationAction) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (themeState.navStyle) {
        NavStyle.TABS -> TabShell(currentTab, pendingResults, onNavigationAction, onLogout, modifier)
        NavStyle.DRAWER -> DrawerShell(currentTab, pendingResults, onNavigationAction, onLogout, modifier)
    }
}
```

`App.kt`의 호출부도 `pendingResults = navUiState.pendingResults`를 함께 넘긴다.

`DestinationContent`도 같은 4개로 줄이고, 내부 화면 호출부에서 기존 콜백을 Action으로 변환한다. 예:

```kotlin
MainDestination.HOME -> HomeScreen(
    onCreatePost = { onNavigationAction(NavigationAction.NavigateToCreatePost(groupId = null)) },
    onOpenPostDetail = { groupId, postId ->
        onNavigationAction(NavigationAction.NavigateToPostDetail(groupId, postId))
    },
    refreshRequested = NavResult.PostCreated(null) in pendingResults,
    onRefreshHandled = {
        onNavigationAction(NavigationAction.ConsumeResult(NavResult.PostCreated(null)))
    },
    onOpenNotifications = { onNavigationAction(NavigationAction.SelectTab(MainDestination.NOTIFICATIONS)) },
    onOpenSearch = { onNavigationAction(NavigationAction.NavigateToSearch) },
    navigationIcon = navigationIcon
)
```

`pendingResults`가 필요하므로 `TabShell`/`DrawerShell`/`DestinationContent`에 `pendingResults: Set<NavResult>` 파라미터를 하나 더 받는다(총 5개).

나머지 목적지의 변환 매핑:

| 화면 | 기존 콜백 | 새 Action |
|---|---|---|
| `GroupsScreen` | `onOpenGroup` | `NavigateToGroupDetail(group.id)` |
| | `onOpenNotifications` | `SelectTab(NOTIFICATIONS)` |
| | `onOpenCreateGroup` | `NavigateToCreateGroup` |
| | `onOpenDiscoverGroups` | `NavigateToDiscoverGroups` |
| | `refreshRequested` | `NavResult.GroupsChanged in pendingResults` |
| | `onRefreshHandled` | `ConsumeResult(NavResult.GroupsChanged)` |
| `ChatScreen` | `onOpenChatRoom` | `NavigateToChatRoom(id, groupId, title)` |
| `FriendsScreen` | `onOpenChatRoom` | `NavigateToChatRoom(id, groupId, title)` |
| | `onOpenUserProfile` | `NavigateToUserProfile(userId)` |
| `ProfileScreen` | `onOpenAccountSettings` | `NavigateToAccountSettings` |
| | `onOpenBlockedUsers` | `NavigateToBlockedUsers` |
| | `onOpenSettings` | `NavigateToAppSettings` |
| 셸 탭/레일 | `onDestinationSelected` | `SelectTab(destination)` |
| 셸 종 아이콘 | `onDestinationSelected(NOTIFICATIONS)` | `SelectTab(NOTIFICATIONS)` |

- [ ] **Step 4: 컴파일 확인**

Run: `gradlew.bat :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Android 동작 확인**

앱 실행 후 확인:
1. 탭 5개 전환
2. 그룹 카드 탭 → 상세 → 뒤로
3. 홈 FAB → 글쓰기 → 작성 → 홈 피드에 새 글
4. 그룹 상세 → 글쓰기 → 작성 → 그룹 피드에 새 글
5. 채팅 허브 → 방 → 아바타 탭 → 프로필 → DM(같은 방이면 닫히기만)
6. 프로필 탭 → 앱 설정 → 뒤로

- [ ] **Step 6: 스테이징**

```bash
git add composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/navigation/AppNavHost.kt \
        composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/App.kt \
        composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/shell/MainShell.kt \
        composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/shell/TabShell.kt \
        composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/shell/DrawerShell.kt
```

---

## Task 6: 셸 내부 화면을 onNavigationAction으로 전환

Task 5에서 셸이 하던 어댑터를 없앤다. 화면이 default parameter로 직접 Action을 올린다.

**Files:**
- Modify: `.../ui/screens/home/HomeScreen.kt`
- Modify: `.../ui/screens/group/GroupsScreen.kt`
- Modify: `.../ui/screens/chat/ChatScreen.kt`
- Modify: `.../ui/screens/friend/FriendsScreen.kt`
- Modify: `.../ui/screens/profile/ProfileScreen.kt` (⚠️ 커밋본이 CRLF — 수정 후 전체 CRLF 복원)
- Modify: `.../ui/shell/TabShell.kt`, `DrawerShell.kt` (어댑터 삭제)

**Interfaces:**
- Consumes: Task 3의 `NavigationAction`/`NavResult`, Task 4의 `sessionNavigationViewModel`
- Produces: 위 5개 화면이 `onNavigationAction: (NavigationAction) -> Unit = sessionNavigationViewModel()::onAction`과 `pendingResults: Set<NavResult> = sessionNavigationViewModel().uiState.collectAsState().value.pendingResults`를 default로 받는다

- [ ] **Step 1: HomeScreen 전환**

시그니처를 다음으로 교체한다:

```kotlin
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    onNavigationAction: (NavigationAction) -> Unit = sessionNavigationViewModel()::onAction,
    pendingResults: Set<NavResult> = sessionNavigationViewModel().uiState.collectAsState().value.pendingResults,
    viewModel: HomeViewModel = sessionViewModel {
        HomeViewModel(
            it.getLoungePostsPagingDataUseCase,
            it.observePostUpdatesUseCase,
            it.observeUserBlocksUseCase,
            it.observePostDeletionsUseCase,
            it.togglePostLikeUseCase
        )
    }
) {
    HomeContent(
        viewModel = viewModel,
        // 라운지 글쓰기 — groupId null이면 홈 피드에 게시된다
        onCreatePost = { onNavigationAction(NavigationAction.NavigateToCreatePost(groupId = null)) },
        onOpenPostDetail = { groupId, postId ->
            onNavigationAction(NavigationAction.NavigateToPostDetail(groupId, postId))
        },
        refreshRequested = NavResult.PostCreated(null) in pendingResults,
        onRefreshHandled = {
            onNavigationAction(NavigationAction.ConsumeResult(NavResult.PostCreated(null)))
        },
        onOpenNotifications = { onNavigationAction(NavigationAction.SelectTab(MainDestination.NOTIFICATIONS)) },
        onOpenSearch = { onNavigationAction(NavigationAction.NavigateToSearch) },
        navigationIcon = navigationIcon,
        modifier = modifier
    )
}
```

`HomeContent`(private)는 그대로 둔다 — 시그니처 변경 없음.

- [ ] **Step 2: 나머지 4개 화면 동일 패턴 적용**

Task 5 Step 3의 매핑 표를 그대로 쓴다. 각 화면의 public `Screen` 컴포저블 시그니처에서 네비 콜백 파라미터를 지우고 `onNavigationAction`/`pendingResults` default 파라미터를 추가한 뒤, private `Content` 컴포저블에 넘기는 자리에서 Action으로 변환한다.

- `GroupsScreen`: `onOpenGroup`, `onOpenNotifications`, `onOpenCreateGroup`, `onOpenDiscoverGroups`, `refreshRequested`, `onRefreshHandled` 제거
- `ChatScreen`: `onOpenChatRoom` 제거
- `FriendsScreen`: `onOpenChatRoom`, `onOpenUserProfile` 제거
- `ProfileScreen`: `onOpenAccountSettings`, `onOpenBlockedUsers`, `onOpenSettings` 제거

- [ ] **Step 3: 셸의 어댑터 삭제**

`TabShell.kt`/`DrawerShell.kt`의 `DestinationContent`에서 각 화면 호출을 인자 없는 형태로 줄인다:

```kotlin
MainDestination.HOME -> HomeScreen(navigationIcon = navigationIcon)
MainDestination.GROUPS -> GroupsScreen(navigationIcon = navigationIcon)
MainDestination.FRIENDS -> FriendsScreen()
MainDestination.CHAT -> ChatScreen()
MainDestination.NOTIFICATIONS -> NotificationsScreen()
MainDestination.PROFILE -> ProfileScreen(onLogout = onLogout)
```

`pendingResults`를 이제 화면이 직접 읽으므로 셸 체인 전체에서 삭제한다 —
`MainShell`, `TabShell`, `DrawerShell`, `DestinationContent` 4곳의 파라미터와 `App.kt`의 인자 1곳.
`MainShell`은 이걸로 최종 5개(`themeState`, `currentTab`, `onNavigationAction`, `onLogout`, `modifier`)가 된다.

- [ ] **Step 4: 컴파일 + 동작 확인**

Run: `gradlew.bat :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL

앱에서 Task 5 Step 5의 1~6번을 다시 확인한다(동작 동일해야 함).

- [ ] **Step 5: CRLF 복원 확인**

```bash
file composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/profile/ProfileScreen.kt
```
출력에 `CRLF`가 없으면 복원한다:
```bash
sed -i 's/$/\r/' composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/profile/ProfileScreen.kt
```

- [ ] **Step 6: 스테이징**

```bash
git add composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/home/HomeScreen.kt \
        composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/group/GroupsScreen.kt \
        composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/chat/ChatScreen.kt \
        composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/friend/FriendsScreen.kt \
        composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/profile/ProfileScreen.kt \
        composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/shell/TabShell.kt \
        composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/shell/DrawerShell.kt
```

---

## Task 7: 오버레이 화면을 onNavigationAction으로 전환

**Files:**
- Modify: `.../ui/screens/group/{GroupDetailScreen,GroupEditScreen,GroupReportsScreen,CreateGroupScreen,DiscoverGroupsScreen}.kt`
- Modify: `.../ui/screens/post/{PostDetailScreen,CreatePostScreen}.kt`
- Modify: `.../ui/screens/chat/ChatRoomScreen.kt`
- Modify: `.../ui/screens/call/CallScreen.kt`
- Modify: `.../ui/screens/search/SearchScreen.kt`
- Modify: `.../ui/screens/settings/{AccountSettingsScreen,BlockedUsersScreen}.kt`
- Modify: `.../ui/screens/user/UserProfileScreen.kt`
- Modify: `.../ui/navigation/AppNavHost.kt` (어댑터 삭제)

**Interfaces:**
- Consumes: Task 3·4
- Produces: 위 화면들이 `onNavigationAction` default 파라미터를 받는다. `AppNavHost`의 각 `composable<>` 블록은 라우트 파라미터만 넘긴다.

- [ ] **Step 1: GroupDetailScreen 전환**

콜백 12개를 지우고 라우트 파라미터 + `onNavigationAction` + `pendingResults`만 받는다:

```kotlin
@Composable
fun GroupDetailScreen(
    groupId: Long,
    modifier: Modifier = Modifier,
    onNavigationAction: (NavigationAction) -> Unit = sessionNavigationViewModel()::onAction,
    pendingResults: Set<NavResult> = sessionNavigationViewModel().uiState.collectAsState().value.pendingResults,
    // ⚠️ 기존 viewModel default parameter 선언을 그대로 둔다 — 파라미터 순서만 뒤로 옮긴다
    viewModel: GroupDetailViewModel = sessionViewModel(key = "group-$groupId") { /* 기존 생성자 인자 그대로 */ }
) {
    GroupDetailContent(
        viewModel = viewModel,
        onBack = { onNavigationAction(NavigationAction.NavigateBack) },
        onCreatePost = { onNavigationAction(NavigationAction.NavigateToCreatePost(groupId)) },
        onOpenPostDetail = { postId -> onNavigationAction(NavigationAction.NavigateToPostDetail(groupId, postId)) },
        onOpenChatRoom = { chatRoomId, gid, title ->
            onNavigationAction(NavigationAction.NavigateToChatRoom(chatRoomId, gid, title))
        },
        refreshRequested = NavResult.PostCreated(groupId) in pendingResults,
        onRefreshHandled = { onNavigationAction(NavigationAction.ConsumeResult(NavResult.PostCreated(groupId))) },
        onGroupClosed = {
            onNavigationAction(NavigationAction.PublishResult(NavResult.GroupsChanged))
            onNavigationAction(NavigationAction.NavigateBack)
        },
        groupUpdateRequested = NavResult.GroupUpdated(groupId) in pendingResults,
        onGroupUpdateHandled = { onNavigationAction(NavigationAction.ConsumeResult(NavResult.GroupUpdated(groupId))) },
        onOpenGroupEdit = { onNavigationAction(NavigationAction.NavigateToGroupEdit(groupId)) },
        onOpenAccountSettings = { onNavigationAction(NavigationAction.NavigateToAccountSettings) },
        onOpenAppSettings = { onNavigationAction(NavigationAction.NavigateToAppSettings) },
        onOpenGroupReports = { onNavigationAction(NavigationAction.NavigateToGroupReports(groupId)) },
        onOpenUserProfile = { userId -> onNavigationAction(NavigationAction.NavigateToUserProfile(userId)) },
        modifier = modifier
    )
}
```

기존 public 시그니처의 콜백들은 private `GroupDetailContent`로 그대로 내려간다(내부 구조 변경 없음).

- [ ] **Step 2: 나머지 화면 전환**

각 화면의 변환 매핑:

| 화면 | 기존 콜백 | 새 Action |
|---|---|---|
| `GroupEditScreen` | `onBack` | `NavigateBack` |
| | `onSaved` | `PublishResult(GroupUpdated(groupId))` + `PublishResult(GroupsChanged)` + `NavigateBack` |
| `GroupReportsScreen` | `onBack` | `NavigateBack` |
| | `onOpenPostDetail` | `NavigateToPostDetail(groupId, postId)` |
| `CreateGroupScreen` | `onBack` | `NavigateBack` |
| `DiscoverGroupsScreen` | `onBack` | `NavigateBack` |
| `PostDetailScreen` | `onBack` | `NavigateBack` |
| | `onEdit` | `NavigateToCreatePost(groupId, postId)` |
| | `onOpenUserProfile` | `NavigateToUserProfile(userId)` |
| | `refreshRequested` | `PostUpdated(groupId, postId) in pendingResults` |
| | `onRefreshHandled` | `ConsumeResult(PostUpdated(groupId, postId))` |
| `CreatePostScreen` | `onBack` | `NavigateBack` |
| | `onCreated` | postId != null && groupId != null 이면 `PublishResult(PostUpdated(groupId, postId))`, 아니면 `PublishResult(PostCreated(groupId))` — 이어서 `NavigateBack` |
| `ChatRoomScreen` | `onBack` | `NavigateBack` |
| | `onStartCall` | `StartCall(chatRoomId, title, video)` |
| | `onOpenUserProfile` | `NavigateToUserProfile(userId)` |
| `CallScreen` | `onBack` | `NavigateBack` |
| `SearchScreen` | `onBack` | `NavigateBack` |
| | `onOpenGroupDetail` | `NavigateToGroupDetail(groupId)` |
| | `onOpenPostDetail` | `NavigateToPostDetail(groupId, postId)` |
| | `onOpenChatRoom` | `NavigateToChatRoom(chatRoomId, groupId, title)` |
| | `onOpenUserProfile` | `NavigateToUserProfile(userId)` |
| `AccountSettingsScreen` | `onBack` | `NavigateBack` |
| `BlockedUsersScreen` | `onBack` | `NavigateBack` |

- [ ] **Step 3: UserProfileScreen 전환 (특수 — underlyingChatRoomId)**

이 화면만 `underlyingChatRoomId`를 알아야 하므로 파라미터로 받는다(AppNavHost가 백스택에서 읽어 넘긴다):

```kotlin
@Composable
fun UserProfileScreen(
    userId: Long,
    /** 바로 아래가 채팅방이면 그 방 id — 같은 방으로 가려 할 때 또 쌓지 않기 위한 판정 재료 */
    underlyingChatRoomId: Long?,
    modifier: Modifier = Modifier,
    onNavigationAction: (NavigationAction) -> Unit = sessionNavigationViewModel()::onAction,
    // ⚠️ 기존 viewModel default parameter 선언을 그대로 둔다
    viewModel: UserProfileViewModel = sessionViewModel(key = "user-$userId") { /* 기존 생성자 인자 그대로 */ }
) {
    UserProfileContent(
        viewModel = viewModel,
        onClose = { onNavigationAction(NavigationAction.NavigateBack) },
        onOpenChatRoom = { chatRoomId, groupId, title ->
            onNavigationAction(
                NavigationAction.OpenChatRoomFromProfile(chatRoomId, groupId, title, underlyingChatRoomId)
            )
        },
        onOpenAccountSettings = { onNavigationAction(NavigationAction.NavigateToAccountSettings) },
        modifier = modifier
    )
}
```

- [ ] **Step 4: AppNavHost 어댑터 삭제**

각 `composable<>` 블록이 라우트 파라미터만 넘기게 줄인다:

```kotlin
composable<Route.GroupDetail> { backStackEntry ->
    val route = backStackEntry.toRoute<Route.GroupDetail>()

    OverlayContainer(isOverlay) {
        GroupDetailScreen(
            groupId = route.groupId,
            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
        )
    }
}
```

`UserProfile` 다이얼로그 블록만 예외다 — 백스택 바로 아래 엔트리를 읽어 화면에 넘겨야 한다:

```kotlin
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
        modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(0.92f)
    )
}
```

이제 `AppNavHost`의 `onNavigationAction`/`pendingResults` 파라미터는 아무도 쓰지 않으므로 시그니처를 줄인다:

```kotlin
@Composable
fun AppNavHost(
    navController: NavHostController,
    themeState: ThemeState,
    isOverlay: Boolean,
    modifier: Modifier = Modifier
)
```

`themeState`는 `AppSettingsScreen`이 받아야 하므로 남긴다.
`App.kt`의 `AppNavHost(...)` 호출부에서도 `onNavigationAction`/`pendingResults` 인자를 지운다.

- [ ] **Step 5: 컴파일 + 전체 동작 확인**

Run: `gradlew.bat :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL

앱에서 확인:
1. Task 5 Step 5의 1~6번
2. 게시글 상세 → 수정 → 저장 → 상세 본문 갱신
3. 그룹 설정 탭 → 그룹 수정 → 저장 → 상세 커버/제목 + 그룹 목록 카드 갱신
4. 그룹 나가기 → 목록에서 사라짐
5. 검색 → 각 섹션 진입
6. 통화 발신(채팅방 → 통화 버튼) / 수신 배너 수락

- [ ] **Step 6: 스테이징**

```bash
git add composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/ \
        composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/navigation/AppNavHost.kt
```

---

## Task 8: Desktop 2-pane

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/App.kt`

**Interfaces:**
- Consumes: Task 1의 `paneModeFor`, Task 4의 `currentTopRouteAsState`, Task 5의 `AppNavHost`
- Produces: 없음 (최종 배선)

- [ ] **Step 1: SessionContent에 2-pane 배치 추가**

`Box { MainShell(); AppNavHost() }`를 아래로 교체한다.

```kotlin
/** 좌측 셸 + 우측 상세로 나누는 최소 폭 — 레일(600dp)보다 넉넉히 위 */
private val TwoPaneBreakpoint = 900.dp
```

```kotlin
val topRoute = navController.currentTopRouteAsState()
val pane = topRoute?.let(::paneModeFor) ?: PaneMode.FULL_SCREEN

BoxWithConstraints {
    val twoPane = maxWidth >= TwoPaneBreakpoint && pane == PaneMode.DETAIL_PANE

    // ⚠️ movableContentOf가 없으면 창 폭이 900dp를 넘나들 때 셸과 NavHost가 재생성되어
    // 백스택·스크롤·Paging 프레젠터가 전부 날아간다. 같은 노드를 부모만 바꿔 옮긴다.
    val shell = remember {
        movableContentOf { m: Modifier ->
            MainShell(
                themeState = themeState,
                currentTab = navUiState.currentTab,
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
                isOverlay = !twoPane,
                modifier = m
            )
        }
    }

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
    // 수신 통화 배너 — Task 5에서 쓴 IncomingCallBanner 블록을 통째로 여기(BoxWithConstraints의
    // 마지막 자식)로 옮긴다. 내용 변경 없음 — Box 스코프라 align(Alignment.TopCenter)도 그대로 동작한다
    incomingCallUiState.incomingCall?.let { call ->
        IncomingCallBanner(
            call = call,
            onAccept = {
                incomingCallViewModel.onAction(IncomingCallViewModel.Action.Dismiss)
                onNavigationAction(
                    NavigationAction.AcceptIncomingCall(
                        chatRoomId = call.chatRoomId,
                        title = call.roomName ?: call.callerName,
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
```

- [ ] **Step 2: Android 회귀 확인 (폰 폭이라 2-pane 미적용)**

Run: `gradlew.bat :composeApp:assembleDebug`
앱에서 Task 7 Step 5의 1~6번을 다시 확인 — 동작이 이전과 같아야 한다.

- [ ] **Step 3: Desktop 2-pane 확인**

Run: `gradlew.bat :composeApp:run`

확인:
1. 창을 넓게(≥900dp) 하고 그룹 탭 → 그룹 카드 클릭 → **우측 패널**에 상세, 좌측에 목록 유지
2. 게시글 카드 클릭 → 우측 패널이 게시글 상세로 교체
3. 채팅 허브 → 방 클릭 → 우측 패널에 채팅방
4. 상세에서 뒤로 → 우측 패널이 사라지고 셸이 전체 폭
5. 설정/글쓰기/그룹 만들기 → **전체를 덮는지** 확인(패널 아님)
6. 공개 프로필 → 중앙 카드 다이얼로그(패널 아님)
7. **창 폭을 900dp 위아래로 천천히 드래그** — 상세를 연 채로 폭을 줄였다 늘려도
   백스택이 유지되고 좌측 목록 스크롤 위치가 그대로인지 확인 (`movableContentOf` 검증의 핵심)

- [ ] **Step 4: 스테이징**

```bash
git add composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/App.kt
```

---

## Task 9: iOS 계약 미러

**Files:**
- Create: `iosApp/iosApp/UI/Navigation/Route.swift`
- Create: `iosApp/iosApp/UI/Navigation/NavResult.swift`
- Create: `iosApp/iosApp/UI/Navigation/NavigationAction.swift`
- Create: `iosApp/iosApp/UI/Navigation/NavigationEvent.swift`
- Create: `iosApp/iosApp/UI/Navigation/NavigationViewModel.swift`
- Create: `iosApp/iosApp/UI/Navigation/NavigationStackCompat.swift`
- Modify: `iosApp/iosApp.xcodeproj/project.pbxproj` (6파일 등록)

**Interfaces:**
- Consumes: 기존 `MviViewModel` 프로토콜 (`iosApp/UI/Mvi/MviViewModel.swift`)
- Produces: Swift `Route`(Hashable), `NavResult`(Hashable), `NavigationAction`, `NavigationEvent`, `NavigationViewModel`(ObservableObject), `NavigationStackCompat<Root>`

- [ ] **Step 1: Route.swift**

```swift
import Foundation

/// 목적지 — composeApp ui/navigation/Route.kt와 1:1 미러.
/// NavigationStack(path:)이 요구하므로 Hashable이어야 한다.
enum Route: Hashable {
    case groupDetail(groupId: Int64)
    case postDetail(groupId: Int64, postId: Int64)
    case chatRoom(chatRoomId: Int64, groupId: Int64?, title: String)
    case userProfile(userId: Int64)
    case createPost(groupId: Int64?, postId: Int64?)
    case groupEdit(groupId: Int64)
    case groupReports(groupId: Int64)
    case call(chatRoomId: Int64, title: String, ring: Bool, video: Bool)
    case accountSettings
    case appSettings
    case blockedUsers
    case createGroup
    case discoverGroups
    case search
}
```

Kotlin의 `Route.Shell`은 Compose NavHost의 시작 목적지 전용이라 Swift에는 두지 않는다 — iOS는 `path`가 비어 있는 상태가 곧 셸이다.

- [ ] **Step 2: NavResult.swift / NavigationAction.swift / NavigationEvent.swift**

```swift
/// 화면 간 결과 신호 — composeApp ui/navigation/NavResult.kt 미러.
/// 이벤트가 아니라 상태인 이유: 결과를 낸 화면이 떠 있는 동안 받을 화면은 뷰 트리에서 빠져 있다
enum NavResult: Hashable {
    case postCreated(groupId: Int64?)
    case postUpdated(groupId: Int64, postId: Int64)
    case groupUpdated(groupId: Int64)
    case groupsChanged
}
```

```swift
/// 화면이 올리는 의도 — composeApp ui/navigation/NavigationAction.kt 미러
enum NavigationAction {
    case navigateToGroupDetail(groupId: Int64)
    case navigateToPostDetail(groupId: Int64, postId: Int64)
    case navigateToChatRoom(chatRoomId: Int64, groupId: Int64?, title: String)
    case navigateToUserProfile(userId: Int64)
    case navigateToCreatePost(groupId: Int64?, postId: Int64?)
    case navigateToGroupEdit(groupId: Int64)
    case navigateToGroupReports(groupId: Int64)
    /// 발신 — 입장+벨울림
    case startCall(chatRoomId: Int64, title: String, video: Bool)
    /// 수신 배너 수락 — 입장만(벨은 다시 울리지 않는다)
    case acceptIncomingCall(chatRoomId: Int64, title: String, video: Bool)
    case navigateToAccountSettings
    case navigateToAppSettings
    case navigateToBlockedUsers
    case navigateToCreateGroup
    case navigateToDiscoverGroups
    case navigateToSearch
    case navigateBack
    case selectTab(destination: SGDestination)
    /// 프로필 시트의 DM 버튼 — 바로 아래가 같은 방이면 또 쌓지 않는다
    case openChatRoomFromProfile(chatRoomId: Int64, groupId: Int64?, title: String, underlyingChatRoomId: Int64?)
    case publishResult(NavResult)
    case consumeResult(NavResult)
}
```

```swift
/// VM → 호스트 이동 지시 — composeApp ui/navigation/NavigationEvent.kt 미러
enum NavigationEvent {
    case navigateTo(Route)
    case navigateBack
}
```

`SGDestination`은 기존 `MainShellView.swift`에 있는 탭 enum이다. Kotlin에서 `MainDestination`을 `ui/navigation`으로 옮긴 것과 맞추려면 `SGDestination`도 `UI/Navigation/`으로 옮기는 것이 일관되지만, Swift는 파일 위치가 네임스페이스에 영향을 주지 않으므로 **이번엔 옮기지 않는다**(pbxproj 변경을 최소화). 주석으로 위치를 남긴다.

- [ ] **Step 3: NavigationViewModel.swift**

```swift
import Foundation
import Combine

/// 네비게이션 — composeApp ui/navigation/NavigationViewModel.kt와 1:1 미러.
/// 탭과 화면 간 결과는 상태로, 오버레이 이동만 이벤트로 뷰에 위임한다.
@MainActor
final class NavigationViewModel: ObservableObject, MviViewModel {

    struct UiState {
        var currentTab: SGDestination = .home
        /// 화면이 재진입해 읽어갈 때까지 남는다
        var pendingResults: Set<NavResult> = []
    }

    @Published private(set) var uiState = UiState()

    let event = PassthroughSubject<NavigationEvent, Never>()

    func onAction(_ action: NavigationAction) {
        switch action {
        case .selectTab(let destination):
            uiState.currentTab = destination
        case .publishResult(let result):
            uiState.pendingResults.insert(result)
        case .consumeResult(let result):
            uiState.pendingResults.remove(result)
        case .navigateBack:
            event.send(.navigateBack)
        case .openChatRoomFromProfile(let chatRoomId, let groupId, let title, let underlying):
            if underlying == chatRoomId {
                event.send(.navigateBack)
            } else {
                event.send(.navigateTo(.chatRoom(chatRoomId: chatRoomId, groupId: groupId, title: title)))
            }
        case .navigateToGroupDetail(let groupId):
            event.send(.navigateTo(.groupDetail(groupId: groupId)))
        case .navigateToPostDetail(let groupId, let postId):
            event.send(.navigateTo(.postDetail(groupId: groupId, postId: postId)))
        case .navigateToChatRoom(let chatRoomId, let groupId, let title):
            event.send(.navigateTo(.chatRoom(chatRoomId: chatRoomId, groupId: groupId, title: title)))
        case .navigateToUserProfile(let userId):
            event.send(.navigateTo(.userProfile(userId: userId)))
        case .navigateToCreatePost(let groupId, let postId):
            event.send(.navigateTo(.createPost(groupId: groupId, postId: postId)))
        case .navigateToGroupEdit(let groupId):
            event.send(.navigateTo(.groupEdit(groupId: groupId)))
        case .navigateToGroupReports(let groupId):
            event.send(.navigateTo(.groupReports(groupId: groupId)))
        case .startCall(let chatRoomId, let title, let video):
            event.send(.navigateTo(.call(chatRoomId: chatRoomId, title: title, ring: true, video: video)))
        case .acceptIncomingCall(let chatRoomId, let title, let video):
            event.send(.navigateTo(.call(chatRoomId: chatRoomId, title: title, ring: false, video: video)))
        case .navigateToAccountSettings: event.send(.navigateTo(.accountSettings))
        case .navigateToAppSettings: event.send(.navigateTo(.appSettings))
        case .navigateToBlockedUsers: event.send(.navigateTo(.blockedUsers))
        case .navigateToCreateGroup: event.send(.navigateTo(.createGroup))
        case .navigateToDiscoverGroups: event.send(.navigateTo(.discoverGroups))
        case .navigateToSearch: event.send(.navigateTo(.search))
        }
    }
}
```

- [ ] **Step 4: NavigationStackCompat.swift**

ConCafe `iosApp/Presentation/Navigation/NavigationStackCompat.swift`를 그대로 포팅한다(`Route` 타입만 StoryGroup 것). iOS 15(iPhone 7) 지원 때문에 필요하다 — `NavigationStack(path:)`은 16+다.

```swift
import SwiftUI

/// iOS 16은 NavigationStack(path:), iOS 15는 재귀 NavigationLink로 같은 path 배열을 재현한다.
/// ConCafe iosApp/Presentation/Navigation/NavigationStackCompat.swift 포팅.
struct NavigationStackCompat<Root: View>: View {
    @Binding private var path: [Route]

    private let root: Root

    private let destination: (Route) -> AnyView

    var body: some View {
        if #available(iOS 16.0, *) {
            NavigationStack(path: $path) {
                root.navigationDestination(for: Route.self) { destination($0) }
            }
        } else {
            LegacyNavigationStack(path: $path, root: root, destination: destination)
        }
    }

    init(
        path: Binding<[Route]>,
        @ViewBuilder root: () -> Root,
        @ViewBuilder destination: @escaping (Route) -> some View
    ) {
        self._path = path
        self.root = root()
        self.destination = { AnyView(destination($0)) }
    }
}

private struct LegacyNavigationStack<Root: View>: View {
    @Binding var path: [Route]

    let root: Root

    let destination: (Route) -> AnyView

    var body: some View {
        NavigationView {
            LegacyNavigationNode(path: $path, depth: 0, root: AnyView(root), destination: destination)
        }
        .navigationViewStyle(StackNavigationViewStyle())
    }
}

private struct LegacyNavigationNode: View {
    @Binding var path: [Route]

    let depth: Int

    let root: AnyView

    let destination: (Route) -> AnyView

    var body: some View {
        ZStack {
            root
            NavigationLink(
                isActive: Binding(
                    get: { path.count > depth },
                    set: { isActive in
                        if !isActive { path = Array(path.prefix(depth)) }
                    }
                ),
                destination: {
                    if path.count > depth {
                        LegacyNavigationNode(
                            path: $path,
                            depth: depth + 1,
                            root: destination(path[depth]),
                            destination: destination
                        )
                    } else {
                        EmptyView()
                    }
                },
                label: { EmptyView() }
            )
            .hidden()
        }
    }
}
```

- [ ] **Step 5: pbxproj에 6파일 등록**

`iosApp/iosApp.xcodeproj/project.pbxproj`에 `UI/Navigation` 그룹과 파일 6개를 등록한다. 기존 등록 패턴(예: `002C` 알림, `002D~F` 채팅)을 따라 새 ID 대역(`0050`~`0055`)을 쓴다. 각 파일마다 `PBXBuildFile`, `PBXFileReference`, 그룹의 `children`, `PBXSourcesBuildPhase`의 `files` 4곳에 항목을 추가한다.

- [ ] **Step 6: 스테이징**

```bash
git add iosApp/iosApp/UI/Navigation/ iosApp/iosApp.xcodeproj/project.pbxproj
```

---

## Task 10: iOS MainShellView 이관

**Files:**
- Modify: `iosApp/iosApp/UI/Shell/MainShellView.swift`
- Modify: `iosApp/iosApp/UI/Shell/TabShellView.swift`
- Modify: `iosApp/iosApp/UI/Shell/DrawerShellView.swift`

**Interfaces:**
- Consumes: Task 9의 Swift 계약 전부
- Produces: `MainShellView`가 `@State path: [Route]` 1개 + `@StateObject navigationViewModel`로 동작. `TabShellView`/`DrawerShellView`는 `current: SGDestination`, `onNavigationAction: (NavigationAction) -> Void`, `container`, VM 3종, `onLogout`만 받는다.

- [ ] **Step 1: MainShellView 상태 교체**

삭제할 `@State`: `showSettings`, `showSearch`, `selectedGroupId`, `showAccountSettings`, `showBlockedUsers`, `selectedChatRoom`, `acceptedCall`, `groupsRefreshPending`, `current`

유지할 `@State`: `selectedUserId`(시트), `profileFollowUp`(시트 dismiss 후 push)

추가:

```swift
@StateObject private var navigationViewModel = NavigationViewModel()

/// 오버레이 백스택 — Compose NavHostController 미러
@State private var path: [Route] = []
```

- [ ] **Step 2: 이벤트 → path 배선**

```swift
var body: some View {
    navigationRoot
        .onReceive(navigationViewModel.event) { event in
            switch event {
            case .navigateTo(let route):
                // 공개 프로필만 시트다(Compose dialog 미러) — path에 쌓지 않는다
                if case .userProfile(let userId) = route {
                    selectedUserId = userId
                } else {
                    path.append(route)
                }
            case .navigateBack:
                if selectedUserId != nil {
                    selectedUserId = nil
                } else if !path.isEmpty {
                    path.removeLast()
                }
            }
        }
        .sheet(isPresented: showUserProfile, onDismiss: runProfileFollowUp) { userProfileDestination }
        .overlay(alignment: .top) { … }   // 수신 통화 배너 — 기존 그대로
}
```

수신 통화 배너의 `onAccept`는 `acceptedCall = CallRef(...)` 대신:

```swift
navigationViewModel.onAction(.acceptIncomingCall(
    chatRoomId: call.chatRoomId,
    title: call.roomName ?? call.callerName,
    video: call.video
))
```

- [ ] **Step 3: navigationRoot를 NavigationStackCompat으로 교체**

`navigationDestination(isPresented:)` 7개 + `NavigationLink(isActive:)` 7개(iOS 15 분기)를 전부 지우고:

```swift
@ViewBuilder private var navigationRoot: some View {
    NavigationStackCompat(path: $path) {
        shellContent
    } destination: { route in
        destinationView(for: route)
    }
}

@ViewBuilder private func destinationView(for route: Route) -> some View {
    switch route {
    case .groupDetail(let groupId):
        GroupDetailView(groupId: groupId, container: container, chatViewModel: chatViewModel,
                        theme: theme, profileViewModel: profileViewModel,
                        onNavigationAction: navigationViewModel.onAction)
    case .chatRoom(let chatRoomId, let groupId, let title):
        ChatRoomView(chatRoomId: chatRoomId, groupId: groupId, title: title,
                     container: container, chatViewModel: chatViewModel)
    case .postDetail(let groupId, let postId):
        PostDetailView(groupId: groupId, postId: postId, container: container,
                       theme: theme, profileViewModel: profileViewModel)
    case .createPost(let groupId, let postId):
        CreatePostView(groupId: groupId, postId: postId, container: container,
                       onNavigationAction: navigationViewModel.onAction)
    case .groupEdit(let groupId):
        GroupEditView(groupId: groupId, container: container,
                      onNavigationAction: navigationViewModel.onAction)
    case .groupReports(let groupId):
        GroupReportsView(groupId: groupId, container: container)
    case .call(let chatRoomId, let title, let ring, let video):
        CallView(chatRoomId: chatRoomId, title: title, ring: ring, video: video, container: container)
    case .accountSettings:
        AccountSettingsView(container: container, profileViewModel: profileViewModel)
    case .appSettings:
        SGSettingsView(theme: theme)
    case .blockedUsers:
        BlockedUsersView(container: container)
    case .createGroup:
        CreateGroupView(container: container)
    case .discoverGroups:
        DiscoverGroupsView(container: container)
    case .search:
        SearchView(container: container, chatViewModel: chatViewModel, theme: theme,
                   profileViewModel: profileViewModel)
    case .userProfile:
        // 시트로 처리한다 — path에 들어오지 않는다
        EmptyView()
    }
}
```

각 View의 실제 생성자 인자는 현재 코드의 `xxxDestination` 프로퍼티에서 그대로 가져온다. 화면 내부 `NavigationLink`는 이번 범위가 아니므로 건드리지 않는다(스펙 6.1의 1단계).

- [ ] **Step 4: shellContent에서 결과 신호/콜백 제거**

`TabShellView`/`DrawerShellView` 호출을 아래로 줄인다:

```swift
@ViewBuilder private var shellContent: some View {
    if theme.navStyle == .tabs {
        TabShellView(
            current: navigationViewModel.uiState.currentTab,
            onNavigationAction: navigationViewModel.onAction,
            pendingResults: navigationViewModel.uiState.pendingResults,
            container: container,
            profile: profileViewModel.uiState.profile,
            notificationsViewModel: notificationsViewModel,
            chatViewModel: chatViewModel,
            profileViewModel: profileViewModel,
            onLogout: onLogout
        )
    } else {
        DrawerShellView( /* 동일 인자 */ )
    }
}
```

두 셸 뷰 내부에서 `onOpenGroup: { selectedGroupId = $0.id }` 같은 어댑터를 `onNavigationAction(.navigateToGroupDetail(groupId: $0.id))`로 바꾸고, `groupsRefreshRequested`는 `pendingResults.contains(.groupsChanged)`로, `onGroupsRefreshHandled`는 `onNavigationAction(.consumeResult(.groupsChanged))`로 바꾼다.

`current`가 `@Binding`에서 값 전달로 바뀌므로 탭 선택은 `onNavigationAction(.selectTab(destination:))`으로 올린다.

- [ ] **Step 5: profileFollowUp 단순화**

```swift
private func runProfileFollowUp() {
    guard let followUp = profileFollowUp else { return }
    profileFollowUp = nil

    switch followUp {
    case .chatRoom(let room):
        path.append(.chatRoom(chatRoomId: room.chatRoomId, groupId: room.groupId, title: room.title))
    case .accountSettings:
        path.append(.accountSettings)
    }
}
```

시트가 완전히 닫힌 뒤에 push해야 유실되지 않는다 — 기존 패턴을 그대로 유지한다.

- [ ] **Step 6: Swift 코드 리뷰 (Mac 미보유로 컴파일 미검증)**

확인 항목:
1. `destinationView(for:)`의 모든 `case`가 실제 존재하는 View 이름·생성자 인자와 일치하는가 (현재 `xxxDestination` 프로퍼티와 대조)
2. `Route`/`NavResult`가 `Hashable`이고 연관값 타입이 전부 `Hashable`인가 (`Int64?`, `String`, `Bool` — OK)
3. `path.append`가 `@MainActor` 컨텍스트에서 호출되는가 (`onReceive`는 메인 큐)
4. iOS 15 분기(`LegacyNavigationNode`)가 `Route`를 제네릭이 아닌 구체 타입으로 받는가
5. `SGDestination`이 `MainShellView.swift`에 그대로 남아 있고 `NavigationAction`에서 참조 가능한가

- [ ] **Step 7: 스테이징**

```bash
git add iosApp/iosApp/UI/Shell/MainShellView.swift \
        iosApp/iosApp/UI/Shell/TabShellView.swift \
        iosApp/iosApp/UI/Shell/DrawerShellView.swift
```

---

## 최종 검증

- [ ] `gradlew.bat :composeApp:jvmTest` — `PaneModeTest` 3 + `NavigationViewModelTest` 6 전부 통과
- [ ] `gradlew.bat :composeApp:assembleDebug` — BUILD SUCCESSFUL
- [ ] `gradlew.bat :composeApp:run` — Desktop 2-pane + 폭 드래그 시 상태 유지
- [ ] `git status`로 스테이징 목록 확인 — 의도한 파일만 올라갔는지, CRLF 잡음이 섞이지 않았는지
- [ ] 사용자에게 커밋 메시지 제안 전달 (커밋은 사용자가 직접)

## 범위 밖 (후속 작업)

- iOS 화면 내부 `NavigationLink` 중앙 `path` 이관 (`GroupDetailView` 15곳, `SearchView` 10곳, `PostDetailView` 8곳 등)
- iPad 2-pane
- 딥링크 / 푸시 알림 → 라우트 복원
- `AuthFlow`(로그인↔가입)는 세션 밖이라 현행 `when(authScreen)` 유지
