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
import androidx.compose.material.TabRowDefaults.tabIndicatorOffset
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
