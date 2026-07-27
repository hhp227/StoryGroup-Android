package kr.hhp227.storygroup.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.filter
import kr.hhp227.storygroup.ui.theme.SgTheme

/** 레거시 collapsing_toolbar_layout_height(256dp)의 2/3 — 사용자 조정(2026-07-19) */
val CollapsingHeaderHeight = 170.dp

/** M2 TopAppBar 기본 높이 — 접힘 구간(스크림 페이드·snap 경계) 계산에 사용 */
private val TopBarHeight = 56.dp

/**
 * 공용 콜랩싱 헤더 스캐폴드 — 레거시 CollapsingToolbarLayout 미러(라운지 홈/그룹 상세 공유).
 * [header]가 목록 첫 아이템으로 스크롤되어 접히고, 오버레이 상단바가 접힘 비율에 따라
 * 투명(흰 콘텐츠)→linen 스크림(잉크)으로 전환된다. 레거시 snap 플래그도 미러.
 * [onRefresh]를 주면 당겨서 새로고침이 붙는다 — 인디케이터는 콜랩싱 헤더 아래에서 내려온다.
 */
@Composable
fun SgCollapsingHeaderScaffold(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: (@Composable () -> Unit)? = null,
    isRefreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    headerHeight: Dp = CollapsingHeaderHeight,
    header: @Composable BoxScope.(listState: LazyListState) -> Unit,
    content: LazyListScope.() -> Unit
) {
    val listState = rememberLazyListState()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val density = LocalDensity.current
    // 접힘 구간 = 헤더 전체 - 핀 되는 상단바(상태바는 양쪽에 공통이라 상쇄) — 레거시 exitUntilCollapsed 미러
    val collapseRangePx = with(density) { (headerHeight - TopBarHeight).toPx() }
    val collapseFraction = rememberCollapseFraction(listState, collapseRangePx)
    // 인디케이터는 콜랩싱 헤더의 현재 하단에서 나온다 — 레거시에서 SwipeRefreshLayout이
    // 앱바 아래(appbar_scrolling_view_behavior) RecyclerView만 감싸던 구조의 미러.
    // 헤더가 접히면 핀 상단바 아래까지만 올라온다
    val indicatorTopPadding by remember(listState, statusBarTop, headerHeight, density) {
        derivedStateOf {
            val collapsed = statusBarTop + TopBarHeight
            val headerBottom = statusBarTop + headerHeight - with(density) { listState.firstVisibleItemScrollOffset.toDp() }
            if (listState.firstVisibleItemIndex > 0) collapsed else headerBottom.coerceAtLeast(collapsed)
        }
    }

    // 레거시 layout_scrollFlags의 snap 미러 — 스크롤이 멎으면 가까운 쪽(펼침/접힘)으로 붙인다
    LaunchedEffect(listState, collapseRangePx) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { !it }
            .collect {
                if (listState.firstVisibleItemIndex == 0) {
                    val offset = listState.firstVisibleItemScrollOffset
                    if (offset > 0 && offset < collapseRangePx) {
                        if (offset < collapseRangePx / 2) listState.animateScrollToItem(0)
                        else listState.animateScrollBy(collapseRangePx - offset)
                    }
                }
            }
    }
    SgPullRefreshBox(
        refreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        indicatorTopPadding = indicatorTopPadding
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "collapsing-header") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(headerHeight + statusBarTop)
                        .clipToBounds()
                ) {
                    header(listState)
                }
            }
            content()
        }
        CollapsingTopBar(
            collapseFraction = collapseFraction.value,
            title = title,
            navigationIcon = navigationIcon,
            actions = actions,
            modifier = Modifier.align(Alignment.TopCenter)
        )
        // 레거시 fab(bottom|end, fab_margin) 미러 — 스크롤과 무관하게 화면 우하단 고정
        if (floatingActionButton != null) {
            Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                floatingActionButton()
            }
        }
    }
}

/** 레거시 layout_collapseMode="parallax" 미러 — 목록이 위로 갈 때 헤더 콘텐츠는 절반 속도로 따라간다 */
fun Modifier.collapsingParallax(listState: LazyListState): Modifier = graphicsLayer {
    translationY = if (listState.firstVisibleItemIndex == 0) {
        listState.firstVisibleItemScrollOffset * 0.5f
    } else 0f
}

/** 0(펼침)→1(접힘) — 헤더 아이템이 접힘 구간을 지나간 비율. 파생 상태라 스크롤 중에만 재계산된다 */
@Composable
private fun rememberCollapseFraction(listState: LazyListState, collapseRangePx: Float): State<Float> =
    remember(listState, collapseRangePx) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / collapseRangePx).coerceIn(0f, 1f)
        }
    }

/**
 * 콜랩싱 상단바 — SgTopBar의 콜랩싱 변형(레거시 contentScrim 미러).
 * 접힘 비율에 따라 배경(linen)·보더가 차오르고 콘텐츠 색이 흰색→잉크로 보간된다.
 */
@Composable
private fun CollapsingTopBar(
    collapseFraction: Float,
    title: String,
    navigationIcon: (@Composable () -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors

    Column(
        modifier
            .fillMaxWidth()
            .background(sg.linen.copy(alpha = collapseFraction))
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        TopAppBar(
            title = {
                Text(title, fontWeight = FontWeight.Bold, color = lerp(Color.White, sg.ink, collapseFraction))
            },
            navigationIcon = navigationIcon,
            actions = actions,
            backgroundColor = Color.Transparent,
            contentColor = lerp(Color.White, sg.inkSoft, collapseFraction),
            elevation = 0.dp
        )
        Divider(color = sg.stoneBorder.copy(alpha = collapseFraction), thickness = 1.dp)
    }
}
