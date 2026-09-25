# 그룹 상세 탭 재편 + 앨범 탭 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 그룹 상세를 레거시식 콜랩싱 커버+탭(소식/앨범/일정/멤버/설정) 구조로 재편하고, 웹 앨범 파생 뷰(`GET /api/groups/{id}/photos`)를 앨범 탭(월별 섹션+3열 그리드)으로 구현한다.

**Architecture:** shared에 `GroupPhoto` 모델+Paging 스트림을 기존 `PagePagingSource` 관용구로 추가(서버 수정 0). Compose는 nestedScroll 오프셋 방식의 `SgCollapsingTabScaffold`를 신설(홈용 기존 스캐폴드 무수정)하고 `HorizontalPager`로 탭별 독립 스크롤. iOS는 기존 단일 ScrollView+스트레치 커버를 유지한 채 인라인 탭바+스크롤 시 고정 오버레이 탭바로 미러(스와이프 없음).

**Tech Stack:** KMP shared(Ktor+kotlinx.serialization+app.cash.paging) / Compose Multiplatform 1.9 (M2) / SwiftUI(iOS 15 폴백 유지) + Jetpack-Paging-for-SwiftUI

**Spec:** `docs/superpowers/specs/2026-08-11-group-detail-tabs-album-design.md`

## Global Constraints

- 리포: KMP `/mnt/c/Users/hong2/IntelliJIDEAProjects/StoryGroup/StoryGroup-Android`(현재 브랜치 `feature/group`). 백엔드·웹 수정 없음.
- **커밋·push 금지 — 스테이징까지만.** 각 태스크의 "스테이징" 단계는 실수정 파일만 경로 지정 `git add`. `git add -A` 금지(워킹트리 전체가 CRLF 플립 노이즈).
- 수정 파일이 CRLF로 플립되면 스테이징 전 LF 정규화: `sed -i 's/\r$//' <file>` (원래 CRLF인 파일은 CRLF 유지 — `git diff` 로 EOL-only 변경이 없는지 확인).
- Android+jvm 컴파일은 Windows gradle: 리포 루트에서 `cmd.exe /c "set JAVA_HOME=C:\Program Files\ojdkbuild\java-17-openjdk-17.0.3.0.6-1&& gradlew.bat <tasks>"`. gradle 출력 파이프 금지(pipefail 함정) — 파일 리다이렉트 후 tail/grep.
- iOS klib 컴파일은 WSL: `JAVA_HOME=$HOME/.local/jdks/jdk-17.0.19+10 ./gradlew :shared:compileKotlinIosArm64` (konan linux prebuilt 기존재).
- Swift는 이 환경에서 빌드 불가 — 문법·미러 정확성만 확보하고 **미검증임을 최종 보고에 명시**.
- M2 material(`androidx.compose.material.*`)만 — M3 import 금지. 아이콘은 core 세트만(`Icons.Default.PlayArrow`는 core에 있음, extended 아티팩트 추가 금지).
- 뷰 생성자 파라미터 순서는 Compose↔iOS 1:1 규칙. iOS VM은 UseCase 생성자 주입(컨테이너 주입 금지), VM 프로퍼티는 풀네임.
- Kotlin 주석에 `/* */` 중첩 금지(예: `video/*` 표기) — 라인 주석 사용.
- `<scratchpad>` = `/tmp/claude-1000/-mnt-c-Users-hong2-IntelliJIDEAProjects-StoryGroup/af575542-1ddf-4823-8eca-f8355c458a6d/scratchpad` (빌드 로그 전용, 리포에 넣지 않는다).

---

### Task 1: shared — GroupPhoto 모델·DTO·Paging 리포지토리·UseCase·Compose DI

**Files:**
- Create: `shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/model/GroupPhoto.kt`
- Create: `shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/usecase/GetGroupPhotosPagingDataUseCase.kt`
- Modify: `shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/network/dto/GroupDtos.kt` (끝에 DTO 2개 추가)
- Modify: `shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/repository/GroupRepository.kt` (메서드 1개, `getMembers` 뒤)
- Modify: `shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/repository/GroupRepositoryImpl.kt` (구현+매퍼)
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/di/AppContainer.kt` (L120 `getGroupPostsPagingDataUseCase` 근처)

**Interfaces:**
- Produces: `GroupPhoto(id: Long, postId: Long, image: String, mediaType: GroupPhotoMediaType, userId: Long, authorName: String, createdAt: String)`, `enum GroupPhotoMediaType { IMAGE, VIDEO }`, `GetGroupPhotosPagingDataUseCase.invoke(groupId: Long): Flow<PagingData<GroupPhoto>>`, Compose DI `container.getGroupPhotosPagingDataUseCase` — Task 2(브리지)·4(VM)·5(앨범 탭)가 이 이름 그대로 소비.

- [ ] **Step 1: 도메인 모델 신설**

`GroupPhoto.kt`:

```kotlin
package kr.hhp227.storygroup.shared.domain.model

/**
 * 그룹 앨범 항목 — 별도 엔티티가 아니라 그룹 게시글 첨부의 파생 뷰(웹 lib/api.ts GroupPhoto 미러).
 * 동영상은 image에 동영상 URL이 그대로 온다(서버 썸네일 없음 — 클라이언트가 첫 프레임을 뽑는다).
 * postId로 원본 게시글로 이동한다(맥락 보존).
 */
data class GroupPhoto(
    val id: Long,
    val postId: Long,
    val image: String,
    val mediaType: GroupPhotoMediaType,
    val userId: Long,
    val authorName: String,
    val createdAt: String
)

enum class GroupPhotoMediaType { IMAGE, VIDEO }
```

- [ ] **Step 2: DTO 추가**

`GroupDtos.kt` 끝에:

```kotlin
// GET /api/groups/{id}/photos 응답 — 웹 lib/api.ts GroupPhoto/GroupPhotosPage와 1:1.
// 다른 목록과 달리 {totalCount, photos} 오브젝트다 — totalCount는 앱에선 미사용(탭 구조라 "N장" 표기 없음)
@Serializable
data class GroupPhotoResponse(
    val id: Long,
    val postId: Long,
    val image: String,
    val mediaType: String = "image",
    val userId: Long,
    val authorName: String,
    val createdAt: String
)

@Serializable
data class GroupPhotosPageResponse(
    val totalCount: Long = 0,
    val photos: List<GroupPhotoResponse> = emptyList()
)
```

- [ ] **Step 3: 리포지토리 인터페이스 + 구현 + 매퍼**

`GroupRepository.kt`의 `getMembers` 아래에:

```kotlin
    /** 그룹 앨범(게시글 첨부 파생 뷰) Paging 스트림 — 최신 게시글 순 */
    fun getGroupPhotosPagingData(groupId: Long): Flow<PagingData<GroupPhoto>>
```

(import `kr.hhp227.storygroup.shared.domain.model.GroupPhoto` 추가)

`GroupRepositoryImpl.kt`의 `getMembers` 아래에(응답이 bare list가 아니라 오브젝트인 점만 다른 피드와 다르다):

```kotlin
    override fun getGroupPhotosPagingData(groupId: Long): Flow<PagingData<GroupPhoto>> =
        Pager(PagePagingConfig) {
            PagePagingSource { page, size ->
                client.get("/api/groups/$groupId/photos") {
                    parameter("page", page)
                    parameter("size", size)
                }.body<GroupPhotosPageResponse>().photos.map { it.toDomain() }
            }
        }.flow
```

파일 하단 매퍼 구역(`GroupResponse.toDomain` 근처)에:

```kotlin
private fun GroupPhotoResponse.toDomain() = GroupPhoto(
    id = id,
    postId = postId,
    image = image,
    // 미지의 값은 IMAGE 폴백 — 서버가 종류를 늘려도 그리드가 죽지 않는다
    mediaType = if (mediaType.equals("video", ignoreCase = true)) GroupPhotoMediaType.VIDEO
        else GroupPhotoMediaType.IMAGE,
    userId = userId,
    authorName = authorName,
    createdAt = createdAt
)
```

(import: `GroupPhotosPageResponse`, `GroupPhotoResponse`, `GroupPhoto`, `GroupPhotoMediaType`)

- [ ] **Step 4: UseCase 신설**

`GetGroupPhotosPagingDataUseCase.kt`:

```kotlin
package kr.hhp227.storygroup.shared.domain.usecase

import app.cash.paging.PagingData
import kotlinx.coroutines.flow.Flow
import kr.hhp227.storygroup.shared.domain.model.GroupPhoto
import kr.hhp227.storygroup.shared.domain.repository.GroupRepository

/** 그룹 앨범 Paging 스트림 — cachedIn 없이 반환하고 각 플랫폼 프레젠테이션 경계에서 캐시한다 */
class GetGroupPhotosPagingDataUseCase(private val groupRepository: GroupRepository) {
    operator fun invoke(groupId: Long): Flow<PagingData<GroupPhoto>> =
        groupRepository.getGroupPhotosPagingData(groupId)
}
```

- [ ] **Step 5: Compose DI 등록**

`AppContainer.kt`의 `val getGroupPostsPagingDataUseCase = ...` 아래에:

```kotlin
    val getGroupPhotosPagingDataUseCase = GetGroupPhotosPagingDataUseCase(groupRepository)
```

(import 추가)

- [ ] **Step 6: 컴파일 검증**

```bash
cd /mnt/c/Users/hong2/IntelliJIDEAProjects/StoryGroup/StoryGroup-Android && \
cmd.exe /c "set JAVA_HOME=C:\Program Files\ojdkbuild\java-17-openjdk-17.0.3.0.6-1&& gradlew.bat :shared:compileKotlinJvm :composeApp:compileKotlinJvm" \
  > <scratchpad>/build-task1.log 2>&1; tail -5 <scratchpad>/build-task1.log
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: 스테이징**

```bash
git add shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/model/GroupPhoto.kt \
  shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/usecase/GetGroupPhotosPagingDataUseCase.kt \
  shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/network/dto/GroupDtos.kt \
  shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/repository/GroupRepository.kt \
  shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/repository/GroupRepositoryImpl.kt \
  composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/di/AppContainer.kt
```

---

### Task 2: shared iosMain — GroupPhoto 페이징 브리지

**Files:**
- Modify: `shared/src/iosMain/kotlin/kr/hhp227/storygroup/shared/bridge/GroupBridges.kt` (끝에 추가)

**Interfaces:**
- Consumes: Task 1의 `GetGroupPhotosPagingDataUseCase`, `GroupPhoto`.
- Produces: `GroupPhotoPagingFlowAdapter`(cachedIn/subscribe — `GroupPagingFlowAdapter`와 동일 규약), `GetGroupPhotosPagingDataUseCase.pagingFlow(groupId: Long)`, `emptyGroupPhotoPagingData()` — Task 8의 Swift `KmpInterop`·VM이 소비.

- [ ] **Step 1: 브리지 추가**

`GroupBridges.kt` 끝에(기존 `DiscoverGroupPagingFlowAdapter` 블록 미러, import `GroupPhoto`·`GetGroupPhotosPagingDataUseCase` 추가):

```kotlin
// 그룹 앨범의 Flow<PagingData<GroupPhoto>> 브리지 — GroupPagingFlowAdapter와 동일 규약

/** 그룹 앨범의 Flow<PagingData> 대응 핸들 — GroupPagingFlowAdapter와 동일 규약 */
class GroupPhotoPagingFlowAdapter internal constructor(
    private val source: Flow<PagingData<GroupPhoto>>,
    private val cached: Boolean = false
) {
    fun cachedIn(): GroupPhotoPagingFlowAdapter = GroupPhotoPagingFlowAdapter(source, cached = true)

    fun subscribe(onEach: (PagingData<GroupPhoto>) -> Unit): FlowSubscription {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val flow = if (cached) source.cachedIn(scope) else source

        scope.launch { flow.collect { onEach(it) } }
        return FlowSubscription(scope)
    }
}

/** Kotlin의 getGroupPhotosPagingDataUseCase(groupId) 호출 대응 — Swift callAsFunction이 감싼다 */
fun GetGroupPhotosPagingDataUseCase.pagingFlow(groupId: Long): GroupPhotoPagingFlowAdapter =
    GroupPhotoPagingFlowAdapter(invoke(groupId))

/** Swift State 기본값용 — Kotlin의 PagingData.empty() 대응 */
fun emptyGroupPhotoPagingData(): PagingData<GroupPhoto> = PagingData.empty()
```

- [ ] **Step 2: iOS klib 컴파일 검증(WSL)**

```bash
cd /mnt/c/Users/hong2/IntelliJIDEAProjects/StoryGroup/StoryGroup-Android && \
JAVA_HOME=$HOME/.local/jdks/jdk-17.0.19+10 ./gradlew :shared:compileKotlinIosArm64 \
  > <scratchpad>/build-task2.log 2>&1; tail -5 <scratchpad>/build-task2.log
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 스테이징**

```bash
git add shared/src/iosMain/kotlin/kr/hhp227/storygroup/shared/bridge/GroupBridges.kt
```

---

### Task 3: Compose — `SgCollapsingTabScaffold` 신설

**Files:**
- Create: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/components/SgCollapsingTabScaffold.kt`
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/components/SgCollapsingHeaderScaffold.kt` — `private fun CollapsingTopBar` → `internal fun CollapsingTopBar` (같은 패키지의 새 스캐폴드가 재사용, 그 외 무수정)

**Interfaces:**
- Consumes: `CollapsingTopBar(collapseFraction, title, navigationIcon, actions, modifier)`, `CollapsingHeaderHeight`, `SgPullRefreshBox(refreshing, onRefresh, modifier, indicatorTopPadding)`.
- Produces: `SgCollapsingTabScaffold(title, tabs: List<String>, pagerState: PagerState, modifier, navigationIcon, actions, floatingActionButton, isRefreshing, onRefresh, headerHeight, header: @Composable BoxScope.(collapseFraction: () -> Float) -> Unit, pageContent: @Composable (page: Int) -> Unit)` — Task 6이 소비.

- [ ] **Step 1: 새 스캐폴드 작성**

```kotlin
package kr.hhp227.storygroup.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.TabRowDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.ui.theme.SgTheme

/** M2 TopAppBar 기본 높이 — SgCollapsingHeaderScaffold의 TopBarHeight와 같은 값(둘 다 private라 중복 선언) */
private val TabScaffoldTopBarHeight = 56.dp

/**
 * 콜랩싱 커버 + 핀 탭바 + HorizontalPager 스캐폴드 — 레거시 GroupDetailFragment
 * (CollapsingToolbarLayout+TabLayout+ViewPager2) 미러. SgCollapsingHeaderScaffold는
 * "헤더=단일 LazyColumn 첫 아이템" 방식이라 탭별 독립 스크롤과 충돌 — 이쪽은 nestedScroll
 * 오프셋으로 커버를 접는다. 위 스크롤은 커버가 먼저 접히고(exitUntilCollapsed), 접혀도
 * 탭바는 핀 상단바 아래 남는다(레거시 toolbar marginBottom=48dp 미러). 플링이 멎으면
 * 가까운 쪽으로 붙는다(snap). 당겨서 새로고침은 스캐폴드가 전체를 감싼다 — 페이지 내부에
 * 두면 nestedScroll 체인상 풀리프레시가 헤더 펼침보다 먼저 오버스크롤을 소비한다.
 */
@Composable
fun SgCollapsingTabScaffold(
    title: String,
    tabs: List<String>,
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: (@Composable () -> Unit)? = null,
    isRefreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    headerHeight: Dp = CollapsingHeaderHeight,
    header: @Composable BoxScope.(collapseFraction: () -> Float) -> Unit,
    pageContent: @Composable (page: Int) -> Unit
) {
    val sg = SgTheme.colors
    val density = LocalDensity.current
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // 접힘 구간 = 헤더 전체 - 핀 상단바(탭바는 그 아래 남는다) — SgCollapsingHeaderScaffold와 동일 규칙
    val collapseRangePx = with(density) { (headerHeight - TabScaffoldTopBarHeight).toPx() }
    // 0(펼침) → collapseRangePx(접힘)
    var headerOffsetPx by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val connection = remember(collapseRangePx) {
        object : NestedScrollConnection {
            // 위로 스크롤(y<0)은 목록보다 커버가 먼저 접힌다 — 레거시 exitUntilCollapsed 미러
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y >= 0f || headerOffsetPx >= collapseRangePx) return Offset.Zero
                val consume = (-available.y).coerceAtMost(collapseRangePx - headerOffsetPx)
                headerOffsetPx += consume
                return Offset(0f, -consume)
            }

            // 아래로 스크롤은 목록이 맨 위에 닿아 남긴 분량으로 커버를 편다
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y <= 0f || headerOffsetPx <= 0f) return Offset.Zero
                val consume = available.y.coerceAtMost(headerOffsetPx)
                headerOffsetPx -= consume
                return Offset(0f, consume)
            }

            // 플링이 끝나면 가까운 쪽으로 붙인다 — 레거시 snap 플래그 미러
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val start = headerOffsetPx
                if (start > 0f && start < collapseRangePx) {
                    val target = if (start < collapseRangePx / 2) 0f else collapseRangePx
                    animate(start, target) { value, _ -> headerOffsetPx = value }
                }
                return Velocity.Zero
            }
        }
    }
    val collapseFraction = {
        if (collapseRangePx > 0f) (headerOffsetPx / collapseRangePx).coerceIn(0f, 1f) else 1f
    }
    val headerOffsetDp = with(density) { headerOffsetPx.toDp() }

    SgPullRefreshBox(
        refreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        // 인디케이터는 탭바 아래에서 내려온다 — 커버가 접히면 그만큼 따라 올라간다
        indicatorTopPadding = statusBarTop + headerHeight - headerOffsetDp + 48.dp
    ) {
        Box(Modifier.fillMaxSize().nestedScroll(connection)) {
            Column(Modifier.fillMaxSize()) {
                // 접히는 커버 — 내부는 고정 높이+상단 고정이라 아래부터 잘려 나간다(CollapsingToolbar 미러)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(statusBarTop + headerHeight - headerOffsetDp)
                        .clipToBounds()
                ) {
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            .height(statusBarTop + headerHeight)
                    ) {
                        header(collapseFraction)
                    }
                }
                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                    backgroundColor = sg.paper,
                    contentColor = sg.accent,
                    indicator = { tabPositions ->
                        TabRowDefaults.Indicator(
                            Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                            color = sg.accent
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, tabTitle ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = {
                                Text(
                                    tabTitle,
                                    fontWeight = if (pagerState.currentPage == index) FontWeight.Bold
                                        else FontWeight.Normal
                                )
                            },
                            selectedContentColor = sg.ink,
                            unselectedContentColor = sg.inkFaint
                        )
                    }
                }
                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                    pageContent(page)
                }
            }
            CollapsingTopBar(
                collapseFraction = collapseFraction(),
                title = title,
                navigationIcon = navigationIcon,
                actions = actions,
                modifier = Modifier.align(Alignment.TopCenter)
            )
            // 레거시 fab(bottom|end) 미러 — 호출부가 탭 조건(소식 탭에서만)을 건다
            if (floatingActionButton != null) {
                Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                    floatingActionButton()
                }
            }
        }
    }
}
```

주의: `TabRowDefaults.Indicator`·`tabIndicatorOffset`는 `androidx.compose.material.TabRowDefaults` 소속(M2). `tabIndicatorOffset` import는 `androidx.compose.material.TabRowDefaults.tabIndicatorOffset`. deprecation 경고가 나오면 경고만 확인하고 진행(빌드 실패 아님).

- [ ] **Step 2: `CollapsingTopBar` 가시성 완화**

`SgCollapsingHeaderScaffold.kt`에서 `private fun CollapsingTopBar(` → `internal fun CollapsingTopBar(` 한 줄만 변경.

- [ ] **Step 3: 컴파일 검증**

```bash
cd /mnt/c/Users/hong2/IntelliJIDEAProjects/StoryGroup/StoryGroup-Android && \
cmd.exe /c "set JAVA_HOME=C:\Program Files\ojdkbuild\java-17-openjdk-17.0.3.0.6-1&& gradlew.bat :composeApp:compileKotlinJvm" \
  > <scratchpad>/build-task3.log 2>&1; tail -5 <scratchpad>/build-task3.log
```

Expected: `BUILD SUCCESSFUL` (신규 파일이 아직 미사용이라 unused 경고는 무시)

- [ ] **Step 4: 스테이징**

```bash
git add composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/components/SgCollapsingTabScaffold.kt \
  composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/components/SgCollapsingHeaderScaffold.kt
```

---

### Task 4: Compose — GroupDetailViewModel에 앨범 Paging 스트림

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/group/GroupDetailViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/group/GroupDetailScreen.kt` (`groupDetailViewModel` 헬퍼의 생성자 호출만 — 화면 재편은 Task 6)

**Interfaces:**
- Consumes: Task 1의 `GetGroupPhotosPagingDataUseCase`, `GroupPhoto`.
- Produces: `UiState.photosPagingData: PagingData<GroupPhoto>` — Task 6이 `collectAsLazyPagingItems`로 소비. 생성자 파라미터 `getGroupPhotosPagingDataUseCase`는 `getGroupPostsPagingDataUseCase` **바로 뒤** 위치(iOS 미러도 동일 순서 — Task 8).

- [ ] **Step 1: VM 확장**

`GroupDetailViewModel.kt`:
1. import에 `GetGroupPhotosPagingDataUseCase`, `GroupPhoto` 추가.
2. 생성자에서 `getGroupPostsPagingDataUseCase: GetGroupPostsPagingDataUseCase,` 바로 뒤에 `getGroupPhotosPagingDataUseCase: GetGroupPhotosPagingDataUseCase,` 추가(private 불필요 — init에서만 사용).
3. `setPagingData` 아래에:

```kotlin
    private fun setPhotosPagingData(pagingData: PagingData<GroupPhoto>) {
        _uiState.update { it.copy(photosPagingData = pagingData) }
    }
```

4. `init`의 피드 구독 아래에:

```kotlin
        // 앨범 탭 — 게시글 첨부의 파생 뷰라 별도 스트림(갱신은 RefreshFeed 이벤트가 피드와 함께 태운다)
        getGroupPhotosPagingDataUseCase(groupId)
            .cachedIn(viewModelScope)
            .onEach(::setPhotosPagingData)
            .launchIn(viewModelScope)
```

5. `UiState`의 `val pagingData: PagingData<Post> = PagingData.empty(),` 아래에:

```kotlin
        // 앨범 탭 전용 — 피드처럼 최신 PagingData를 상태에 담는다(Paging-CRUD 샘플 패턴)
        val photosPagingData: PagingData<GroupPhoto> = PagingData.empty(),
```

- [ ] **Step 2: VM 헬퍼 배선**

`GroupDetailScreen.kt`의 `groupDetailViewModel` 헬퍼에서 `getGroupPostsPagingDataUseCase = container.getGroupPostsPagingDataUseCase,` 바로 뒤에:

```kotlin
            getGroupPhotosPagingDataUseCase = container.getGroupPhotosPagingDataUseCase,
```

- [ ] **Step 3: 컴파일 검증**

Task 3 Step 3과 동일 명령(`build-task4.log`). Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: 스테이징**

```bash
git add composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/group/GroupDetailViewModel.kt \
  composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/group/GroupDetailScreen.kt
```

---

### Task 5: Compose — 앨범 탭 컴포저블(`GroupAlbumTab.kt`)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/group/GroupAlbumTab.kt`

**Interfaces:**
- Consumes: Task 1의 `GroupPhoto`/`GroupPhotoMediaType`, 기존 `rememberVideoFrame(url)`, `SgEmptyState`, `SgPagingFooter`.
- Produces: `GroupAlbumTab(lazyPagingItems: LazyPagingItems<GroupPhoto>, onOpenPostDetail: (postId: Long) -> Unit, modifier)` — Task 6이 소비.

- [ ] **Step 1: 파일 작성**

```kotlin
package kr.hhp227.storygroup.ui.screens.group

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.cash.paging.LoadStateError
import app.cash.paging.LoadStateLoading
import app.cash.paging.compose.LazyPagingItems
import coil3.compose.AsyncImage
import kr.hhp227.storygroup.shared.domain.model.GroupPhoto
import kr.hhp227.storygroup.shared.domain.model.GroupPhotoMediaType
import kr.hhp227.storygroup.ui.components.SgEmptyState
import kr.hhp227.storygroup.ui.components.SgPagingFooter
import kr.hhp227.storygroup.ui.theme.SgTheme
import kr.hhp227.storygroup.ui.util.rememberVideoFrame

/**
 * 앨범 탭 — 그룹 게시글 첨부(사진/동영상)의 파생 뷰(웹 /groups/[id]/photos·레거시 AlbumFragment 미러).
 * 월별 섹션 헤더 + 3열 정사각 그리드. 셀 탭 → 원본 게시글 상세(맥락 보존, 라이트박스 없음).
 * 목록이 최신 게시글 순이라 월 경계는 순서대로 끊기만 하면 된다(웹 monthLabel 미러).
 * iosApp GroupAlbumTab.swift와 1:1 미러.
 */
@Composable
internal fun GroupAlbumTab(
    lazyPagingItems: LazyPagingItems<GroupPhoto>,
    onOpenPostDetail: (postId: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors
    val refreshState = lazyPagingItems.loadState.refresh
    val appendState = lazyPagingItems.loadState.append

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        when {
            lazyPagingItems.itemCount == 0 && refreshState is LoadStateLoading -> item(
                key = "album-loading",
                span = { GridItemSpan(maxLineSpan) }
            ) {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = sg.accent)
                }
            }
            lazyPagingItems.itemCount == 0 && refreshState is LoadStateError -> item(
                key = "album-error",
                span = { GridItemSpan(maxLineSpan) }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        refreshState.error.message ?: "사진을 불러오지 못했습니다.",
                        style = SgTheme.typography.bodyMedium,
                        color = sg.rust
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = lazyPagingItems::retry) {
                        Text("다시 시도", color = sg.accent)
                    }
                }
            }
            lazyPagingItems.itemCount == 0 -> item(key = "album-empty", span = { GridItemSpan(maxLineSpan) }) {
                SgEmptyState(
                    title = "아직 사진이 없습니다",
                    subtitle = "게시글에 사진이나 동영상을 올리면 여기에 모여요.",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp)
                )
            }
            else -> {
                // Paging 그리드에 가변 위치 헤더를 끼우려면 items(count) 대신 개별 item()으로 펼친다.
                // 스냅샷은 월 경계 계산 전용 — 로드 트리거는 셀 안의 lazyPagingItems[index]가 담당한다
                // (스냅샷만 쓰면 위치 힌트가 없어 무한 스크롤이 죽는다).
                val snapshot = lazyPagingItems.itemSnapshotList.items
                var lastMonth: String? = null
                snapshot.forEachIndexed { index, photo ->
                    val month = monthLabel(photo.createdAt)
                    if (month != lastMonth) {
                        lastMonth = month
                        item(key = "month-$month", span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                month,
                                style = SgTheme.typography.titleSmall,
                                color = sg.inkSoft,
                                modifier = Modifier.padding(top = if (index == 0) 0.dp else 12.dp, bottom = 4.dp)
                            )
                        }
                    }
                    item(key = "photo-${photo.id}") {
                        val loaded = lazyPagingItems[index] ?: photo
                        AlbumCell(photo = loaded, onClick = { onOpenPostDetail(loaded.postId) })
                    }
                }
                if (appendState is LoadStateLoading || appendState is LoadStateError) {
                    item(key = "album-footer", span = { GridItemSpan(maxLineSpan) }) {
                        SgPagingFooter(
                            isLoadingMore = appendState is LoadStateLoading,
                            error = (appendState as? LoadStateError)?.error?.message,
                            onRetry = lazyPagingItems::retry
                        )
                    }
                }
            }
        }
    }
}

/** "2026-08-03T…" → "2026년 8월" — 서버 ISO-8601 원문에서 잘라 만든다(웹 monthLabel 미러) */
private fun monthLabel(createdAt: String): String {
    val year = createdAt.take(4)
    val month = createdAt.drop(5).take(2).trimStart('0')
    return "${year}년 ${month}월"
}

/**
 * 그리드 정사각 칸 하나 — 사진/동영상/GIF를 종류에 맞게 그린다(웹 MediaThumb 미러).
 * 동영상: 서버 썸네일이 없어 첫 프레임(rememberVideoFrame)을 포스터로 쓰고 ▶로 구분
 * (Desktop은 디코더가 없어 검은 칸+▶ 폴백 — 기존 한계 그대로).
 * GIF: 그리드에선 뱃지만 단다 — 재생은 게시글 상세 몫.
 */
@Composable
private fun AlbumCell(photo: GroupPhoto, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val sg = SgTheme.colors

    Box(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(sg.linen)
            .clickable(onClick = onClick)
    ) {
        when (photo.mediaType) {
            GroupPhotoMediaType.IMAGE -> {
                AsyncImage(
                    model = photo.image,
                    contentDescription = "${photo.authorName}의 사진",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
                // URL 저장 규칙상 확장자가 보존되므로(UUID.확장자) GIF는 경로 끝으로 판별한다(웹 미러)
                if (photo.image.substringBefore('?').substringBefore('#').lowercase().endsWith(".gif")) {
                    Text(
                        "GIF",
                        style = SgTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
            GroupPhotoMediaType.VIDEO -> {
                val frame = rememberVideoFrame(photo.image)
                if (frame != null) {
                    Image(
                        bitmap = frame,
                        contentDescription = "${photo.authorName}의 동영상",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                } else {
                    Box(Modifier.matchParentSize().background(Color.Black))
                }
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(34.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
```

주의: `sg.linen`이 팔레트에 없으면(컴파일 에러로 확인) `SgColors.kt`에서 실재하는 옅은 배경 토큰으로 대체(`accentSoft` 등 — 이름은 파일에서 확인). Coil3 `AsyncImage` import 경로는 기존 `SgAvatar.kt`의 것을 그대로 따른다.

- [ ] **Step 2: 컴파일 검증**

Task 3 Step 3과 동일 명령(`build-task5.log`). Expected: `BUILD SUCCESSFUL` (미사용 internal 경고 무시)

- [ ] **Step 3: 스테이징**

```bash
git add composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/group/GroupAlbumTab.kt
```

---

### Task 6: Compose — GroupDetailScreen 5탭 재편

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/group/GroupDetailScreen.kt`

**Interfaces:**
- Consumes: Task 3 `SgCollapsingTabScaffold`, Task 4 `UiState.photosPagingData`, Task 5 `GroupAlbumTab`.
- Produces: 화면 공개 시그니처(`GroupDetailScreen(groupId, onBack, onCreatePost, onOpenPostDetail, onOpenChatRoom, refreshRequested, onRefreshHandled, modifier, viewModel)`) **무변경** — 라우트 배선 수정 없음.

- [ ] **Step 1: `GroupDetailContent` 골격 교체**

`SgCollapsingHeaderScaffold` 호출을 `SgCollapsingTabScaffold`로 교체한다. 핵심 변경(기존 코드는 유지하되 배치만 이동):

```kotlin
    // 레거시 R.array.tab_name(소식/앨범/맴버/설정)에 일정 추가 — 일정·설정은 빈 화면(추후 구현)
    val tabs = remember { listOf("소식", "앨범", "일정", "멤버", "설정") }
    val pagerState = rememberPagerState { tabs.size }
    // 피드와 동일 관용구 — 상태에서 photosPagingData만 뽑아낸 스트림을 수집
    val photosPagingDataFlow = remember(viewModel) {
        viewModel.uiState.map { it.photosPagingData }.distinctUntilChanged()
    }
    val photoLazyPagingItems = photosPagingDataFlow.collectAsLazyPagingItems()
```

이벤트 처리에서 앨범도 함께 갱신(앨범은 게시글 첨부의 파생 뷰 — 웹 refreshKey 미러):

```kotlin
                GroupDetailViewModel.Event.RefreshFeed -> {
                    lazyPagingItems.refresh()
                    photoLazyPagingItems.refresh()
                }
```

스캐폴드 호출(제목/내비게이션/액션/커버 콘텐츠는 기존 그대로 이식):

```kotlin
    SgCollapsingTabScaffold(
        title = uiState.group?.name.orEmpty(),
        tabs = tabs,
        pagerState = pagerState,
        navigationIcon = { /* 기존 뒤로 IconButton 그대로 */ },
        actions = { /* 기존 채팅 IconButton 그대로 */ },
        // 레거시 isTabPositionZero 미러 — 글쓰기 FAB는 소식 탭에서만
        floatingActionButton = if (pagerState.currentPage == 0) {
            { /* 기존 FloatingActionButton 그대로 */ }
        } else null,
        // 스피너는 데이터가 이미 있는 갱신에만(첫 로드는 각 탭의 중앙 스피너 담당 — 기존 규칙 유지)
        isRefreshing = uiState.isLoading && uiState.group != null,
        // 현재 탭 무관하게 상세+피드+앨범 함께 갱신 — iOS .refreshable과 대칭(단순 우선)
        onRefresh = {
            viewModel.onAction(GroupDetailViewModel.Action.Refresh)
            viewModel.onAction(GroupDetailViewModel.Action.RefreshFeed)
        },
        header = { collapseFraction ->
            // 기존 header 블록 그대로 — 단 listState가 없어졌으므로
            // `.collapsingParallax(listState)` 만 제거(커버는 높이 접힘으로 잘려 나간다)
        },
        modifier = modifier
    ) { page ->
        when (page) {
            0 -> GroupFeedTab(
                uiState = uiState,
                lazyPagingItems = lazyPagingItems,
                onRetryDetail = { viewModel.onAction(GroupDetailViewModel.Action.Refresh) },
                onToggleLike = { viewModel.onAction(GroupDetailViewModel.Action.ToggleLike(it)) },
                onShare = { share(postShareText(it)) },
                onOpenPostDetail = onOpenPostDetail
            )
            1 -> GroupAlbumTab(
                lazyPagingItems = photoLazyPagingItems,
                onOpenPostDetail = onOpenPostDetail
            )
            2 -> SgEmptyState(
                title = "일정",
                subtitle = "준비 중입니다.",
                modifier = Modifier.fillMaxSize()
            )
            3 -> GroupMembersTab(
                uiState = uiState,
                onApprove = { viewModel.onAction(GroupDetailViewModel.Action.ApproveJoinRequest(it)) },
                onReject = { viewModel.onAction(GroupDetailViewModel.Action.RejectJoinRequest(it)) },
                onShowInvite = { showInviteDialog = true },
                onMemberClick = { dmTargetMember = it }
            )
            else -> SgEmptyState(
                title = "설정",
                subtitle = "준비 중입니다.",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
```

기존 `InviteDialog`/`DmConfirmDialog`/`likeError` AlertDialog 블록은 스캐폴드 호출 뒤 그대로 유지.

- [ ] **Step 2: 소식 탭 추출(`GroupFeedTab`)**

기존 스캐폴드 content 람다의 피드 부분(detail-error/feed-loading/feed-error/feed-empty/items/feed-footer)을 private 컴포저블로 이식. **join-requests·invite-code·members 아이템은 이 탭에서 제거**(멤버 탭으로 이동). 이 파일 안에 추가:

```kotlin
/** 소식 탭 — 기존 피드 목록 그대로(인박스·멤버 섹션은 멤버 탭으로 이동). iosApp feedTab 미러 */
@Composable
private fun GroupFeedTab(
    uiState: GroupDetailViewModel.UiState,
    lazyPagingItems: LazyPagingItems<Post>,
    onRetryDetail: () -> Unit,
    onToggleLike: (Post) -> Unit,
    onShare: (Post) -> Unit,
    onOpenPostDetail: (postId: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors
    val refreshState = lazyPagingItems.loadState.refresh
    val appendState = lazyPagingItems.loadState.append

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (uiState.error != null) {
            item(key = "detail-error") { /* 기존 detail-error 블록 그대로, 재시도 = onRetryDetail */ }
        }
        when {
            lazyPagingItems.itemCount == 0 && refreshState is LoadStateLoading -> item(key = "feed-loading") {
                /* 기존 그대로 */
            }
            lazyPagingItems.itemCount == 0 && refreshState is LoadStateError -> item(key = "feed-error") {
                /* 기존 그대로 */
            }
            lazyPagingItems.itemCount == 0 -> item(key = "feed-empty") { /* 기존 그대로 */ }
            else -> {
                items(count = lazyPagingItems.itemCount, key = lazyPagingItems.itemKey(Post::id)) { index ->
                    lazyPagingItems[index]?.let { post ->
                        SgPostCard(
                            post,
                            Modifier.padding(horizontal = 16.dp),
                            onToggleLike = { onToggleLike(post) },
                            onShare = { onShare(post) }
                        ) { onOpenPostDetail(post.id) }
                    }
                }
                if (appendState is LoadStateLoading || appendState is LoadStateError) {
                    item(key = "feed-footer") { /* 기존 SgPagingFooter 그대로 */ }
                }
            }
        }
    }
}
```

("기존 그대로" 블록은 현재 파일의 해당 item 블록을 문자 그대로 옮긴다 — 내용 변경 금지.)

- [ ] **Step 3: 멤버 탭 추출(`GroupMembersTab`) + `MemberStrip` 삭제**

```kotlin
/**
 * 멤버 탭 — 가입 신청 인박스+초대코드(모더레이터, 멤버 관리 성격이라 여기 모음) 위에
 * 4열 멤버 그리드(레거시 MemberFragment 미러 — 기존 수평 MemberStrip 대체).
 * 타인을 탭하면 1:1 DM 확인으로 이어진다. iosApp membersTab 미러.
 */
@Composable
private fun GroupMembersTab(
    uiState: GroupDetailViewModel.UiState,
    onApprove: (Long) -> Unit,
    onReject: (Long) -> Unit,
    onShowInvite: () -> Unit,
    onMemberClick: (GroupMember) -> Unit,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (uiState.joinRequests.isNotEmpty()) {
            item(key = "join-requests", span = { GridItemSpan(maxLineSpan) }) {
                JoinRequestInbox(
                    requests = uiState.joinRequests,
                    processingUserId = uiState.processingRequestUserId,
                    actionError = uiState.actionError,
                    onApprove = onApprove,
                    onReject = onReject
                )
            }
        }
        if (uiState.canModerate) {
            item(key = "invite-code", span = { GridItemSpan(maxLineSpan) }) {
                OutlinedButton(
                    onClick = onShowInvite,
                    shape = SgTheme.shapes.button,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("초대코드 만들기", style = SgTheme.typography.labelLarge, color = sg.accent)
                }
            }
        }
        item(key = "member-count", span = { GridItemSpan(maxLineSpan) }) {
            Text("멤버 ${uiState.visibleMembers.size}", style = SgTheme.typography.titleSmall, color = sg.ink)
        }
        items(uiState.visibleMembers, key = GroupMember::userId) { member ->
            // 본인은 DM 대상이 아니라 탭도 막는다(서버도 self-DM은 400)
            val isSelf = member.userId == uiState.myUserId

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = if (isSelf) Modifier else Modifier.clickable { onMemberClick(member) }
            ) {
                SgAvatar(member.name, imageUrl = member.profileImg)
                Spacer(Modifier.height(4.dp))
                Text(
                    member.name,
                    style = SgTheme.typography.labelSmall,
                    color = sg.inkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
```

`MemberStrip` 컴포저블은 삭제(유일한 사용처였다). grid용 `items`는 `androidx.compose.foundation.lazy.grid.items` import(기존 `lazy.items`와 충돌 시 별칭).

- [ ] **Step 4: import 정리**

추가: `rememberPagerState`(androidx.compose.foundation.pager), `SgCollapsingTabScaffold`, `LazyVerticalGrid`/`GridCells`/`GridItemSpan`(foundation.lazy.grid), `LazyColumn`(foundation.lazy — 기존에 없으면). 제거: `SgCollapsingHeaderScaffold`, `collapsingParallax`, `LazyRow`(MemberStrip 삭제로) 등 미사용 import.

- [ ] **Step 5: 컴파일 검증(Android+jvm)**

```bash
cd /mnt/c/Users/hong2/IntelliJIDEAProjects/StoryGroup/StoryGroup-Android && \
cmd.exe /c "set JAVA_HOME=C:\Program Files\ojdkbuild\java-17-openjdk-17.0.3.0.6-1&& gradlew.bat :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinJvm" \
  > <scratchpad>/build-task6.log 2>&1; tail -5 <scratchpad>/build-task6.log
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: 스테이징**

```bash
git add composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/group/GroupDetailScreen.kt
```

---

### Task 7: Android APK 빌드 검증

**Files:** 없음(검증 전용)

- [ ] **Step 1: APK 빌드**

```bash
cd /mnt/c/Users/hong2/IntelliJIDEAProjects/StoryGroup/StoryGroup-Android && \
cmd.exe /c "set JAVA_HOME=C:\Program Files\ojdkbuild\java-17-openjdk-17.0.3.0.6-1&& gradlew.bat :composeApp:assembleDebug" \
  > <scratchpad>/build-task7.log 2>&1; tail -5 <scratchpad>/build-task7.log
```

Expected: `BUILD SUCCESSFUL`. 실패 시 로그의 첫 에러부터 수정(systematic-debugging) — 새 코드 원인일 때만 고치고, 기존 워킹트리 문제면 보고.

---

### Task 8: iOS 미러 — VM·화면 재편·앨범 탭·pbxproj

**Files:**
- Modify: `iosApp/iosApp/UI/Paging/KmpInterop.swift` (publisher+extension 추가)
- Modify: `iosApp/iosApp/DI/AppContainer.swift` (usecase 1개)
- Modify: `iosApp/iosApp/UI/Screens/Group/GroupDetailViewModel.swift`
- Modify: `iosApp/iosApp/UI/Screens/Group/GroupDetailView.swift` (탭 재편)
- Create: `iosApp/iosApp/UI/Screens/Group/GroupAlbumTab.swift`
- Modify: `iosApp/iosApp.xcodeproj/project.pbxproj` (신규 1파일 등록)

**Interfaces:**
- Consumes: Task 2 브리지(`GroupPhotoPagingFlowAdapter`·`pagingFlow(groupId:)`·`emptyGroupPhotoPagingData()`), Task 4와 동일한 생성자 파라미터 순서.
- Produces: `GroupAlbumTab(photoItems: LazyPagingItems<GroupPhoto>, groupId: Int64, container: AppContainer)` — GroupDetailView가 소비.

- [ ] **Step 1: KmpInterop.swift — 퍼블리셔 추가**

`GroupPagingPublisher` 블록 뒤에(기존 미러):

```swift
// Kotlin: getGroupPhotosPagingDataUseCase(groupId) → Flow<PagingData<GroupPhoto>>
extension GetGroupPhotosPagingDataUseCase {
    func callAsFunction(groupId: Int64) -> GroupPhotoPagingPublisher {
        GroupPhotoPagingPublisher(adapter: pagingFlow(groupId: groupId))
    }
}

// Kotlin의 Flow<PagingData<GroupPhoto>> 대응 퍼블리셔 — GroupPagingPublisher의 GroupPhoto 타입 대응
struct GroupPhotoPagingPublisher: Publisher {
    typealias Output = PagingData<GroupPhoto>

    typealias Failure = Never

    fileprivate let adapter: GroupPhotoPagingFlowAdapter

    func cachedIn() -> GroupPhotoPagingPublisher {
        GroupPhotoPagingPublisher(adapter: adapter.cachedIn())
    }

    func receive<S>(subscriber: S) where S: Subscriber, S.Input == Output, S.Failure == Never {
        KotlinFlowPublisher<Output> { onEach in
            self.adapter.subscribe(onEach: onEach)
        }
        .receive(subscriber: subscriber)
    }
}
```

- [ ] **Step 2: AppContainer.swift — usecase 등록**

프로퍼티 목록의 `let getGroupPostsPagingDataUseCase: GetGroupPostsPagingDataUseCase` 아래에 선언을, init의 대응 라인 아래에 대입을 추가:

```swift
    let getGroupPhotosPagingDataUseCase: GetGroupPhotosPagingDataUseCase
```

```swift
        getGroupPhotosPagingDataUseCase = GetGroupPhotosPagingDataUseCase(groupRepository: groupRepository)
```

- [ ] **Step 3: GroupDetailViewModel.swift — photos 스트림**

Kotlin Task 4와 1:1:
1. `UiState`의 `var pagingData` 아래에:

```swift
        // 앨범 탭 전용 — 피드처럼 최신 PagingData를 상태에 담는다(Compose photosPagingData 미러)
        var photosPagingData: PagingData<GroupPhoto> = GroupBridgesKt.emptyGroupPhotoPagingData()
```

2. `setPagingData` 아래에:

```swift
    private func setPhotosPagingData(_ pagingData: PagingData<GroupPhoto>) {
        uiState.photosPagingData = pagingData
    }
```

3. init 파라미터 `getGroupPostsPagingDataUseCase` **바로 뒤**에 `getGroupPhotosPagingDataUseCase: GetGroupPhotosPagingDataUseCase,` 추가(Compose와 동일 순서), init 본문의 피드 구독 아래에:

```swift
        // 앨범 탭 — 게시글 첨부의 파생 뷰라 별도 스트림(갱신은 refreshFeed 이벤트가 피드와 함께 태운다)
        getGroupPhotosPagingDataUseCase(groupId: groupId)
            .cachedIn()
            .sink { [weak self] in self?.setPhotosPagingData($0) }
            .store(in: &cancellables)
```

4. `GroupDetailView.swift`의 `GroupDetailView.init` 생성자 호출에도 같은 위치에 `getGroupPhotosPagingDataUseCase: container.getGroupPhotosPagingDataUseCase,` 추가.

- [ ] **Step 4: GroupAlbumTab.swift 신설**

```swift
import SwiftUI
import Paging
import Shared

/// 앨범 탭 — Compose GroupAlbumTab.kt 미러(웹 /groups/[id]/photos·레거시 AlbumFragment).
/// 월별 섹션 + 3열 정사각 그리드, 셀 탭 → 원본 게시글 상세 push(맥락 보존, 라이트박스 없음).
/// 섹션 계산은 peek(로드 트리거 없음), 로드 트리거는 셀의 get이 담당(Compose lazyPagingItems[index] 미러).
struct GroupAlbumTab: View {
    @ObservedObject var photoItems: LazyPagingItems<GroupPhoto>

    let groupId: Int64

    /// 게시글 상세 push의 VM 생성에 쓰인다
    let container: AppContainer

    @Environment(\.sgColors) private var colors

    private static let columns = [
        GridItem(.flexible(), spacing: 4),
        GridItem(.flexible(), spacing: 4),
        GridItem(.flexible(), spacing: 4)
    ]

    /// 목록이 최신 게시글 순이라 순서대로 끊기만 하면 된다(웹 monthLabel 미러)
    private var sections: [(label: String, indices: [Int])] {
        var result: [(label: String, indices: [Int])] = []

        for index in 0..<photoItems.itemCount {
            guard let photo = photoItems.peek(index) else { continue }
            let label = Self.monthLabel(photo.createdAt)

            if result.last?.label == label {
                result[result.count - 1].indices.append(index)
            } else {
                result.append((label, [index]))
            }
        }
        return result
    }

    var body: some View {
        let refreshState = photoItems.loadState.refresh
        let appendState = photoItems.loadState.append

        VStack(alignment: .leading, spacing: 12) {
            if photoItems.itemCount == 0, refreshState is LoadState.Loading {
                ProgressView().frame(maxWidth: .infinity).padding(.vertical, 48)
            } else if photoItems.itemCount == 0, refreshState is LoadState.Error {
                VStack(spacing: 8) {
                    Text("사진을 불러오지 못했습니다.").font(.subheadline).foregroundColor(colors.rust)
                    Button("다시 시도") { photoItems.retry() }
                        .font(.subheadline)
                        .foregroundColor(colors.accent)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 48)
            } else if photoItems.itemCount == 0 {
                SGEmptyState(title: "아직 사진이 없습니다", subtitle: "게시글에 사진이나 동영상을 올리면 여기에 모여요.")
                    .padding(.vertical, 48)
            } else {
                ForEach(sections, id: \.label) { section in
                    Text(section.label)
                        .font(.subheadline.bold())
                        .foregroundColor(colors.inkSoft)
                    LazyVGrid(columns: Self.columns, spacing: 4) {
                        ForEach(section.indices, id: \.self) { index in
                            cell(index)
                        }
                    }
                }
                SGPagingFooter(
                    error: appendState is LoadState.Error ? "사진을 더 불러오지 못했습니다." : nil,
                    isLoadingMore: appendState is LoadState.Loading,
                    onRetry: { photoItems.retry() }
                )
            }
        }
        .padding(.horizontal, 16)
    }

    private func cell(_ index: Int) -> some View {
        // get 접근이 Paging에 위치 힌트를 줘 다음 페이지를 당긴다 — peek만 쓰면 무한 스크롤이 죽는다
        let photo = photoItems.get(index)

        return NavigationLink {
            if let photo {
                PostDetailView(container: container, groupId: groupId, postId: photo.postId)
            }
        } label: {
            // scaledToFill은 명시 프레임 필수 — GeometryReader로 셀 크기를 고정한다(그룹 그리드 커버 픽스 미러)
            GeometryReader { geometry in
                ZStack {
                    if let photo {
                        AlbumThumb(photo: photo)
                    } else {
                        colors.accentSoft
                    }
                }
                .frame(width: geometry.size.width, height: geometry.size.width)
                .clipped()
            }
            .aspectRatio(1, contentMode: .fit)
        }
        .buttonStyle(.plain)
        .cornerRadius(10)
    }

    /// "2026-08-03T…" → "2026년 8월" — 서버 ISO-8601 원문에서 잘라 만든다(Compose monthLabel 미러)
    private static func monthLabel(_ createdAt: String) -> String {
        let year = createdAt.prefix(4)
        var month = createdAt.dropFirst(5).prefix(2)

        while month.hasPrefix("0") { month = month.dropFirst() }
        return "\(year)년 \(month)월"
    }
}

/// 그리드 정사각 칸 하나 — 사진/동영상/GIF를 종류에 맞게 그린다(웹 MediaThumb·Compose AlbumCell 미러).
/// 동영상: 서버 썸네일이 없어 첫 프레임(VideoPosterLoader)을 포스터로 쓰고 ▶로 구분.
private struct AlbumThumb: View {
    let photo: GroupPhoto

    @Environment(\.sgColors) private var colors

    @State private var poster: UIImage?

    var body: some View {
        ZStack {
            if photo.mediaType == .video {
                if let poster {
                    Image(uiImage: poster).resizable().scaledToFill()
                } else {
                    Color.black
                }
                Circle()
                    .fill(Color.black.opacity(0.55))
                    .frame(width: 34, height: 34)
                    .overlay(
                        Image(systemName: "play.fill")
                            .font(.system(size: 13))
                            .foregroundColor(.white)
                    )
            } else {
                AsyncImage(url: URL(string: photo.image)) { phase in
                    if case .success(let image) = phase {
                        image.resizable().scaledToFill()
                    } else {
                        colors.accentSoft
                    }
                }
                // URL 저장 규칙상 확장자가 보존되므로 GIF는 경로 끝으로 판별한다(웹 미러)
                if photo.image.components(separatedBy: "?")[0].components(separatedBy: "#")[0]
                    .lowercased().hasSuffix(".gif") {
                    VStack {
                        Spacer()
                        HStack {
                            Spacer()
                            Text("GIF")
                                .font(.caption2.bold())
                                .foregroundColor(.white)
                                .padding(.horizontal, 5)
                                .padding(.vertical, 1)
                                .background(Color.black.opacity(0.55))
                                .cornerRadius(6)
                                .padding(6)
                        }
                    }
                }
            }
        }
        .task(id: photo.image) {
            if photo.mediaType == .video, poster == nil {
                poster = await VideoPosterLoader.load(photo.image)
            }
        }
    }
}
```

주의: `colors.accentSoft`가 iOS 팔레트에 없으면 SGTheme.swift에서 실재하는 옅은 배경 토큰으로 대체. `.task(id:)`는 iOS 15+ OK. `GroupPhoto.mediaType == .video`는 Kotlin enum의 ObjC 브리지 — 비교가 안 되면 `photo.mediaType == GroupPhotoMediaType.video`로 풀네임.

- [ ] **Step 5: GroupDetailView.swift 탭 재편**

1. 상태 추가:

```swift
    /// 레거시 R.array.tab_name(소식/앨범/맴버/설정)에 일정 추가 — Compose tabs 미러
    private static let tabs = ["소식", "앨범", "일정", "멤버", "설정"]

    /// 선택 탭 — Compose pagerState.currentPage 미러(iOS는 스와이프 없이 탭 터치만 — 플랫폼 관용 예외)
    @State private var selectedTab = 0
```

2. `GroupDetailContent`에 앨범 페이징 수집 추가 — `init`에서 피드와 동일 관용구:

```swift
        let photosPublisher = viewModel.$uiState.map { $0.photosPagingData }.removeDuplicates { $0 === $1 }
        _photoLazyPagingItems = StateObject(wrappedValue: photosPublisher.collectAsLazyPagingItems())
```

(프로퍼티 `@StateObject private var photoLazyPagingItems: LazyPagingItems<GroupPhoto>` 추가)

3. `core`의 ScrollView 본문을 커버+탭바+탭 콘텐츠로:

```swift
            ScrollView {
                VStack(spacing: 12) {
                    cover(topInset: outer.safeAreaInsets.top)
                    tabBar
                    tabContent
                }
                .padding(.bottom, 16)
            }
```

4. 탭바(인라인)와, 스크롤로 커버가 접힌 뒤 상단 고정 오버레이(내비바 스크림과 같은 신호 재사용):

```swift
    /// 하단 인디케이터 탭바 — Compose TabRow 미러. 인라인으로 흐르다가 barScrimVisible이면
    /// 오버레이 사본이 내비바 아래 고정된다(핀 탭바 미러 — 단일 ScrollView라 stickyHeader가 없다)
    private var tabBar: some View {
        HStack(spacing: 0) {
            ForEach(Array(Self.tabs.enumerated()), id: \.offset) { index, title in
                Button { selectedTab = index } label: {
                    VStack(spacing: 6) {
                        Text(title)
                            .font(.subheadline.weight(selectedTab == index ? .bold : .regular))
                            .foregroundColor(selectedTab == index ? colors.ink : colors.inkFaint)
                        Rectangle()
                            .fill(selectedTab == index ? colors.accent : Color.clear)
                            .frame(height: 2)
                    }
                    .padding(.top, 10)
                }
                .buttonStyle(.plain)
                .frame(maxWidth: .infinity)
            }
        }
        .background(colors.paper)
    }
```

`core`의 modifier 체인에(기존 `.overlay(alignment: .bottomTrailing)` FAB 앞에):

```swift
        .overlay(alignment: .top) {
            if barScrimVisible {
                tabBar
            }
        }
```

5. 탭 콘텐츠 스위치 — 기존 `content`를 `feedTab`으로 개명하고 인박스/초대코드/멤버 스트립 부분 제거(피드 에러/로딩/목록만 유지):

```swift
    @ViewBuilder private var tabContent: some View {
        switch selectedTab {
        case 0: feedTab
        case 1: GroupAlbumTab(photoItems: photoLazyPagingItems, groupId: viewModel.groupId, container: container)
        case 2: SGEmptyState(title: "일정", subtitle: "준비 중입니다.").padding(.vertical, 48)
        case 3: membersTab
        default: SGEmptyState(title: "설정", subtitle: "준비 중입니다.").padding(.vertical, 48)
        }
    }
```

6. 멤버 탭 — 기존 `memberStrip`을 대체(삭제)하고, 인박스+초대코드 버튼+4열 그리드(Compose GroupMembersTab 미러):

```swift
    /// 멤버 탭 — 인박스+초대코드(멤버 관리 성격이라 여기 모음)+4열 그리드(레거시 MemberFragment 미러)
    @ViewBuilder private var membersTab: some View {
        VStack(alignment: .leading, spacing: 12) {
            if !viewModel.uiState.joinRequests.isEmpty {
                joinRequestInbox
            }
            if viewModel.uiState.canModerate {
                inviteButton
            }
            Text("멤버 \(viewModel.uiState.visibleMembers.count)")
                .font(.subheadline.bold())
                .foregroundColor(colors.ink)
            LazyVGrid(columns: [
                GridItem(.flexible(), spacing: 12, alignment: .top),
                GridItem(.flexible(), spacing: 12, alignment: .top),
                GridItem(.flexible(), spacing: 12, alignment: .top),
                GridItem(.flexible(), spacing: 12, alignment: .top)
            ], spacing: 16) {
                ForEach(viewModel.uiState.visibleMembers, id: \.userId) { member in
                    Button(action: { dmTargetMember = member }) {
                        VStack(spacing: 4) {
                            SGAvatar(name: member.name, imageUrl: member.profileImg)
                            Text(member.name)
                                .font(.caption2)
                                .foregroundColor(colors.inkSoft)
                                .lineLimit(1)
                        }
                    }
                    .buttonStyle(.plain)
                    // 본인은 DM 대상이 아니라 탭도 막는다(서버도 self-DM은 400)
                    .disabled(member.userId == viewModel.uiState.myUserId)
                }
            }
        }
        .padding(.horizontal, 16)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
```

7. FAB를 소식 탭 전용으로(기존 overlay 수정):

```swift
        .overlay(alignment: .bottomTrailing) {
            // 레거시 isTabPositionZero 미러 — 글쓰기 FAB는 소식 탭에서만
            if selectedTab == 0 {
                SGFab(action: { showCreatePost = true }).padding(16)
            }
        }
```

8. 갱신 경로에 앨범 합류 — `.refreshable`에 `photoLazyPagingItems.refresh()` 추가, `.onReceive` `case .refreshFeed:`에서 둘 다 refresh:

```swift
            case .refreshFeed:
                lazyPagingItems.refresh()
                photoLazyPagingItems.refresh()
```

- [ ] **Step 6: pbxproj 등록**

다음으로 빈 ID를 먼저 확인:

```bash
grep -o 'AABBCCDDEEFF00[0-9A-F][0-9A-F]' iosApp/iosApp.xcodeproj/project.pbxproj | sort -u | tail -3
```

마지막이 `0035`(PostDetailView)면 `0036` 사용. 4곳에 삽입(PostDetailView.swift 각 항목 바로 아래, 그룹 children은 **Screens/Group 그룹**의 `GroupDetailView.swift` 항목 아래):

1. PBXBuildFile 섹션: `A1010036AABBCCDDEEFF0036 /* GroupAlbumTab.swift in Sources */ = {isa = PBXBuildFile; fileRef = A1011036AABBCCDDEEFF0036 /* GroupAlbumTab.swift */; };`
2. PBXFileReference 섹션: `A1011036AABBCCDDEEFF0036 /* GroupAlbumTab.swift */ = {isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = GroupAlbumTab.swift; sourceTree = "<group>"; };`
3. Group(폴더) children: `A1011036AABBCCDDEEFF0036 /* GroupAlbumTab.swift */,`
4. Sources 빌드 페이즈: `A1010036AABBCCDDEEFF0036 /* GroupAlbumTab.swift in Sources */,`

- [ ] **Step 7: 자체 점검(빌드 불가 환경)**

- Swift 파일들에서 참조하는 심볼이 전부 실재하는지 재확인: `SGEmptyState`/`SGPagingFooter`/`SGAvatar`/`SGFab` 시그니처(SGComponents.swift), `VideoPosterLoader.load`, `colors.*` 토큰명(SGTheme.swift), `PostDetailView(container:groupId:postId:)`.
- Compose↔iOS 뷰 파라미터 순서 1:1 확인(`GroupAlbumTab`: photoItems/lazyPagingItems → groupId(iOS만 — Compose는 라우트가 앎) → container).
- pbxproj 4곳 삽입 후 `plutil`은 없으니 중괄호/세미콜론 육안 확인.

- [ ] **Step 8: 스테이징**

```bash
git add iosApp/iosApp/UI/Paging/KmpInterop.swift \
  iosApp/iosApp/DI/AppContainer.swift \
  iosApp/iosApp/UI/Screens/Group/GroupDetailViewModel.swift \
  iosApp/iosApp/UI/Screens/Group/GroupDetailView.swift \
  iosApp/iosApp/UI/Screens/Group/GroupAlbumTab.swift \
  iosApp/iosApp.xcodeproj/project.pbxproj
```

---

### Task 9: 최종 점검 — EOL·스테이징 정리·커밋 메시지

**Files:** 문서 2개 스테이징 + 점검 전용

- [ ] **Step 1: EOL 노이즈 점검**

```bash
cd /mnt/c/Users/hong2/IntelliJIDEAProjects/StoryGroup/StoryGroup-Android && git diff --cached --stat
git diff --cached | grep -c $'\r' || true
```

CR이 섞인 파일이 있으면 `sed -i 's/\r$//' <file>` 후 재스테이징(원래 CRLF였던 파일은 제외 — `git show HEAD:<file> | file -` 로 확인).

- [ ] **Step 2: 스펙·플랜 문서 스테이징**

```bash
git add docs/superpowers/specs/2026-08-11-group-detail-tabs-album-design.md \
  docs/superpowers/plans/2026-08-11-group-detail-tabs-album.md
```

- [ ] **Step 3: 최종 빌드 재확인**

Task 7 명령 재실행(`build-task9.log`). Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: 커밋 메시지 전달(커밋은 사용자)**

사용자에게 아래 메시지 제안(단일 커밋, 브랜치 `feature/group` — 다른 브랜치를 원하면 사용자가 결정):

```
그룹 상세 탭 재편(소식/앨범/일정/멤버/설정) + 앨범 탭

- shared: GroupPhoto 모델+GET /api/groups/{id}/photos Paging 스트림(서버 수정 0)
- Compose: SgCollapsingTabScaffold 신설(nestedScroll 콜랩싱+핀 탭바+HorizontalPager),
  앨범 탭(월별 섹션+3열 그리드, 탭→원본 게시글), 멤버 탭(인박스+초대코드+4열 그리드)
- iOS: 인라인+고정 오버레이 탭바 미러, GroupAlbumTab.swift(pbxproj 0039), 탭 터치 전환
- 일정·설정 탭은 빈 화면(추후 구현)
```

최종 보고에 명시: Swift/Mac 미검증, 데스크톱은 컴파일 검증만(런타임 확인은 사용자 `gradlew :composeApp:run`).
