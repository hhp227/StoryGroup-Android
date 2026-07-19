package kr.hhp227.storygroup.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.filter
import kr.hhp227.storygroup.shared.domain.model.Post
import kr.hhp227.storygroup.ui.components.SgAvatar
import kr.hhp227.storygroup.ui.components.SgCard
import kr.hhp227.storygroup.ui.components.SgEmptyState
import kr.hhp227.storygroup.ui.theme.SgTheme
import kr.hhp227.storygroup.ui.util.formatRelativeTime
import org.jetbrains.compose.resources.painterResource
import storygroup.composeapp.generated.resources.Res
import storygroup.composeapp.generated.resources.header

/** 레거시 collapsing_toolbar_layout_height(256dp)의 2/3 — 사용자 조정(2026-07-19) */
private val HeaderHeight = 170.dp

/** M2 TopAppBar 기본 높이 — 접힘 구간(스크림 페이드·snap 경계) 계산에 사용 */
private val TopBarHeight = 56.dp

/**
 * 홈(라운지) 피드 — 웹 메인 피드 미러 + 레거시 CollapsingToolbar 헤더 이식.
 * 셸이 아닌 화면이 상단바를 직접 그린다: 펼침(사진 위 투명 바+흰 콘텐츠) → 접힘(linen 스크림+잉크).
 * iosApp HomeView.swift와 1:1 미러
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenNotifications: () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val sg = SgTheme.colors
    val listState = rememberLazyListState()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // 접힘 구간 = 헤더 전체 - 핀 되는 상단바(상태바는 양쪽에 공통이라 상쇄) — 레거시 exitUntilCollapsed 미러
    val collapseRangePx = with(LocalDensity.current) { (HeaderHeight - TopBarHeight).toPx() }
    val collapseFraction = rememberCollapseFraction(listState, collapseRangePx)

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
    Box(modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "lounge-header") {
                ParallaxHeaderImage(listState = listState, height = HeaderHeight + statusBarTop)
            }
            when {
                uiState.posts.isEmpty() && uiState.isLoading -> item(key = "feed-loading") {
                    Box(
                        Modifier.fillParentMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = sg.accent)
                    }
                }
                uiState.posts.isEmpty() && uiState.error != null -> item(key = "feed-error") {
                    Column(
                        modifier = Modifier.fillParentMaxWidth().padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(uiState.error.orEmpty(), style = SgTheme.typography.bodyMedium, color = sg.rust)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { viewModel.onAction(HomeViewModel.Action.Refresh) }) {
                            Text("다시 시도", color = sg.accent)
                        }
                    }
                }
                uiState.posts.isEmpty() -> item(key = "feed-empty") {
                    SgEmptyState(
                        title = "아직 이야기가 없습니다",
                        subtitle = "첫 이야기를 남겨보세요.",
                        modifier = Modifier.fillParentMaxWidth().padding(vertical = 48.dp)
                    )
                }
                else -> {
                    items(uiState.posts, key = Post::id) { post ->
                        FeedPostCard(post, Modifier.padding(horizontal = 16.dp))
                    }
                    if (uiState.hasMore || uiState.isLoadingMore || uiState.error != null) {
                        item(key = "feed-footer") {
                            FeedFooter(
                                isLoadingMore = uiState.isLoadingMore,
                                error = uiState.error,
                                postCount = uiState.posts.size,
                                onLoadMore = { viewModel.onAction(HomeViewModel.Action.LoadMore) }
                            )
                        }
                    }
                }
            }
        }
        HomeCollapsingTopBar(
            collapseFraction = collapseFraction.value,
            title = "우리들의 이야기",
            navigationIcon = navigationIcon,
            actions = {
                IconButton(onClick = { /* TODO: 검색 */ }) {
                    Icon(Icons.Default.Search, contentDescription = "검색")
                }
                IconButton(onClick = onOpenNotifications) {
                    Icon(Icons.Default.Notifications, contentDescription = "알림")
                }
            },
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
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

/** 레거시 layout_collapseMode="parallax" 미러 — 목록이 위로 갈 때 이미지는 절반 속도로 따라간다 */
@Composable
private fun ParallaxHeaderImage(listState: LazyListState, height: Dp) {
    Box(Modifier.fillMaxWidth().height(height).clipToBounds()) {
        Image(
            painter = painterResource(Res.drawable.header),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    translationY = if (listState.firstVisibleItemIndex == 0) {
                        listState.firstVisibleItemScrollOffset * 0.5f
                    } else 0f
                }
        )
        // 펼침 상태에서 흰 제목/아이콘 대비 확보 — 레거시 AppBarOverlay 다크 톤의 역할 미러
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.35f),
                    0.5f to Color.Transparent
                )
            )
        )
    }
}

/**
 * 홈 전용 콜랩싱 상단바 — SgTopBar의 콜랩싱 변형(레거시 contentScrim 미러).
 * 접힘 비율에 따라 배경(linen)·보더가 차오르고 콘텐츠 색이 흰색→잉크로 보간된다.
 */
@Composable
private fun HomeCollapsingTopBar(
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

/** 웹 sentinel 미러 — 푸터가 화면에 들어오면 다음 페이지를 읽고, 실패 시엔 수동 재시도만 노출 */
@Composable
private fun FeedFooter(
    isLoadingMore: Boolean,
    error: String?,
    postCount: Int,
    onLoadMore: () -> Unit
) {
    val sg = SgTheme.colors

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when {
            error != null -> {
                Text(error, style = SgTheme.typography.bodySmall, color = sg.rust)
                TextButton(onClick = onLoadMore) {
                    Text("다시 시도", color = sg.accent)
                }
            }
            isLoadingMore -> CircularProgressIndicator(color = sg.accent, modifier = Modifier.padding(8.dp))
            // 페이지가 붙어 postCount가 바뀔 때마다 다시 평가 — 푸터가 계속 보이면 이어서 읽는다
            else -> LaunchedEffect(postCount) { onLoadMore() }
        }
    }
}

@Composable
private fun FeedPostCard(post: Post, modifier: Modifier = Modifier) {
    val sg = SgTheme.colors

    SgCard(modifier = modifier.fillMaxWidth(), onClick = { /* TODO: 게시글 상세 */ }) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SgAvatar(post.authorName)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(post.authorName, style = SgTheme.typography.titleSmall, color = sg.ink)
                    Text(
                        formatRelativeTime(post.createdAt),
                        style = SgTheme.typography.bodySmall,
                        color = sg.inkFaint
                    )
                }
                if (post.isNotice) {
                    Text(
                        "공지",
                        style = SgTheme.typography.labelSmall,
                        color = sg.accent,
                        modifier = Modifier
                            .background(sg.accentSoft, SgTheme.shapes.button)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            if (post.text.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    post.text,
                    style = SgTheme.typography.bodyMedium,
                    color = sg.ink,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // TODO: 첨부 이미지는 이미지 로딩 도입(다음 단계 ④) 후 실제 렌더링으로 교체
            val attachmentSummary = buildList {
                if (post.imageUrls.isNotEmpty()) add("사진 ${post.imageUrls.size}장")
                if (post.videoUrls.isNotEmpty()) add("동영상 ${post.videoUrls.size}개")
            }.joinToString(" · ")
            if (attachmentSummary.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(attachmentSummary, style = SgTheme.typography.bodySmall, color = sg.inkSoft)
            }
        }
    }
}
