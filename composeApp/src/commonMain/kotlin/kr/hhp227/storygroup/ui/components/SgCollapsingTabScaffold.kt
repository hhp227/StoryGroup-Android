package kr.hhp227.storygroup.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
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
import androidx.compose.material.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
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

/** 탭바 높이(M2 Tab 표준 48dp) — 레거시 toolbar marginBottom=48dp(접힘 시 남는 탭 줄)와 같은 값 */
val CollapsingTabRowHeight = 48.dp

/** 레거시 TabLayout tabTextColor(#FFE1E3E5) — 펼침 상태(이미지 위) 비선택 탭 텍스트 */
private val TabTextOnImageUnselected = Color(0xFFE1E3E5)

/**
 * 콜랩싱 커버 + 핀 탭바 + HorizontalPager 스캐폴드 — 레거시 GroupDetailFragment
 * (CollapsingToolbarLayout+TabLayout+ViewPager2) 미러. SgCollapsingHeaderScaffold는
 * "헤더=단일 LazyColumn 첫 아이템" 방식이라 탭별 독립 스크롤과 충돌 — 이쪽은 nestedScroll
 * 오프셋으로 커버를 접는다. 탭바는 레거시 TabLayout(layout_gravity=bottom, 투명 배경)처럼
 * 커버 안 하단에 겹쳐 이미지가 탭 뒤까지 깔린다 — 탭 텍스트는 흰색 계열로 시작해 접힘
 * 비율에 따라 잉크로 보간되고, 커버엔 linen 스크림이 차오른다(레거시 contentScrim 미러).
 * 위 스크롤은 커버가 먼저 접히고(exitUntilCollapsed), 접혀도 탭바는 핀 상단바 아래
 * 남는다(레거시 toolbar marginBottom=48dp 미러). 플링이 멎으면 가까운 쪽으로 붙는다(snap).
 * 당겨서 새로고침은 스캐폴드가 전체를 감싼다 — 페이지 내부에 두면 nestedScroll 체인상
 * 풀리프레시가 헤더 펼침보다 먼저 오버스크롤을 소비한다.
 * [header]는 탭바 영역까지 포함한 커버 전체를 채운다 — 하단 오버레이 콘텐츠는
 * [CollapsingTabRowHeight]만큼 바닥 패딩을 줘야 탭에 가리지 않는다.
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
    // 0(펼침) → collapseRangePx(접힘). saveable — 라우트 재진입(채팅방 등을 다녀온 복귀) 시
    // 접힘 상태가 유지돼야 복원되는 리스트 스크롤 위치와 화면이 일관된다
    var headerOffsetPx by rememberSaveable { mutableStateOf(0f) }
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
        indicatorTopPadding = statusBarTop + headerHeight + CollapsingTabRowHeight - headerOffsetDp
    ) {
        Box(Modifier.fillMaxSize().nestedScroll(connection)) {
            Column(Modifier.fillMaxSize()) {
                // 접히는 커버(탭바 영역 포함) — 내부는 고정 높이+상단 고정이라 아래부터 잘려
                // 나가고, 탭바만 하단에 붙어 함께 올라온다(CollapsingToolbar+TabLayout 미러)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(statusBarTop + headerHeight + CollapsingTabRowHeight - headerOffsetDp)
                        .clipToBounds()
                ) {
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            .height(statusBarTop + headerHeight + CollapsingTabRowHeight)
                    ) {
                        header(collapseFraction)
                    }
                    // 레거시 contentScrim 미러 — 접힐수록 커버 전체가 linen으로 덮인다
                    // (CollapsingTopBar 배경과 같은 팔레트라 접힘 상태가 한 면으로 읽힌다)
                    Box(Modifier.matchParentSize().background(sg.linen.copy(alpha = collapseFraction())))
                    TabRow(
                        selectedTabIndex = pagerState.currentPage,
                        backgroundColor = Color.Transparent,
                        contentColor = sg.accent,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        indicator = { tabPositions ->
                            TabRowDefaults.Indicator(
                                Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                                color = sg.accent
                            )
                        },
                        // 레거시 TabLayout엔 하단 구분선이 없다 — 이미지 위 기본 divider 제거
                        divider = {}
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
                                // 이미지 위(레거시 white/#FFE1E3E5)에서 접히면 linen 위 잉크로 보간
                                selectedContentColor = lerp(Color.White, sg.ink, collapseFraction()),
                                unselectedContentColor = lerp(TabTextOnImageUnselected, sg.inkFaint, collapseFraction())
                            )
                        }
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
