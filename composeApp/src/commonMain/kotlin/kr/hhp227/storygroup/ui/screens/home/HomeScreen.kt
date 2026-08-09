package kr.hhp227.storygroup.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.cash.paging.LoadStateError
import app.cash.paging.LoadStateLoading
import app.cash.paging.compose.collectAsLazyPagingItems
import app.cash.paging.compose.itemKey
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kr.hhp227.storygroup.di.sessionViewModel
import kr.hhp227.storygroup.shared.domain.model.Post
import kr.hhp227.storygroup.ui.components.SgBellAction
import kr.hhp227.storygroup.ui.components.SgCollapsingHeaderScaffold
import kr.hhp227.storygroup.ui.components.SgEmptyState
import kr.hhp227.storygroup.ui.components.SgPagingFooter
import kr.hhp227.storygroup.ui.components.SgPostCard
import kr.hhp227.storygroup.ui.components.collapsingParallax
import kr.hhp227.storygroup.ui.screens.notification.sessionNotificationsViewModel
import kr.hhp227.storygroup.ui.theme.SgTheme
import org.jetbrains.compose.resources.painterResource
import storygroup.composeapp.generated.resources.Res
import storygroup.composeapp.generated.resources.header

/**
 * 홈(라운지) 피드 — 웹 메인 피드 미러 + 레거시 CollapsingToolbar 헤더(SgCollapsingHeaderScaffold).
 * ViewModel은 화면이 default parameter로 세션 스코프에 선언한다(ConCafe 패턴).
 * 화면·프레젠터는 셸의 keep-alive 컨테이너 안에서 dispose되지 않아 스크롤이 유지된다.
 * 계층은 iosApp HomeView.swift와 1:1 미러 — Screen=상태 소유(VM 선언), Content=구독+UI.
 */
@Composable
fun HomeScreen(
    onCreatePost: () -> Unit,
    // 라운지 글도 그룹 글이라 상세는 그 글의 groupId로 들어간다
    onOpenPostDetail: (groupId: Long, postId: Long) -> Unit,
    // 글쓰기 성공 신호(라운지) — 소비 후 onRefreshHandled로 소거한다
    refreshRequested: Boolean,
    onRefreshHandled: () -> Unit,
    onOpenNotifications: () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    viewModel: HomeViewModel = sessionViewModel { HomeViewModel(it.getLoungePostsPagingDataUseCase) }
) {
    HomeContent(
        viewModel = viewModel,
        onCreatePost = onCreatePost,
        onOpenPostDetail = onOpenPostDetail,
        refreshRequested = refreshRequested,
        onRefreshHandled = onRefreshHandled,
        onOpenNotifications = onOpenNotifications,
        navigationIcon = navigationIcon,
        modifier = modifier
    )
}

@Composable
private fun HomeContent(
    viewModel: HomeViewModel,
    onCreatePost: () -> Unit,
    // 라운지 글도 그룹 글이라 상세는 그 글의 groupId로 들어간다
    onOpenPostDetail: (groupId: Long, postId: Long) -> Unit,
    refreshRequested: Boolean,
    onRefreshHandled: () -> Unit,
    onOpenNotifications: () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null
) {
    // 상태에서 pagingData만 뽑아낸 스트림을 수집 — Paging-CRUD 샘플·iOS($state.map)와 동일 관용구
    val pagingDataFlow = remember(viewModel) {
        viewModel.uiState.map { it.pagingData }.distinctUntilChanged()
    }
    val lazyPagingItems = pagingDataFlow.collectAsLazyPagingItems()
    val sg = SgTheme.colors
    // 로딩/에러/빈 상태는 Paging3 LoadState로 그린다 — 다음 페이지 트리거는 prefetchDistance가 담당
    val refreshState = lazyPagingItems.loadState.refresh
    // 종 아이콘 뱃지 — 알림 화면과 같은 세션 VM의 미읽음 수
    val notificationsUiState by sessionNotificationsViewModel().uiState.collectAsState()

    // 작성 화면에서 돌아온 결과 — 라운지 피드를 첫 페이지부터 다시 읽는다
    LaunchedEffect(refreshRequested) {
        if (refreshRequested) {
            viewModel.onAction(HomeViewModel.Action.Refresh)
            onRefreshHandled()
        }
    }
    // VM의 일회성 갱신 이벤트 — 프레젠터 refresh()가 활성 PagingSource를 무효화해
    // 같은 스트림이 새 세대(첫 페이지, 라운지 재해석 포함)를 방출한다
    LaunchedEffect(Unit) {
        viewModel.event.collect { event ->
            when (event) {
                HomeViewModel.Event.Refresh -> lazyPagingItems.refresh()
            }
        }
    }
    SgCollapsingHeaderScaffold(
        title = "우리들의 이야기",
        navigationIcon = navigationIcon,
        actions = {
            IconButton(onClick = { /* TODO: 검색 */ }) {
                Icon(Icons.Default.Search, contentDescription = "검색")
            }
            SgBellAction(
                unreadCount = notificationsUiState.unreadCount,
                onClick = onOpenNotifications
            )
        },
        // 레거시 fragment_lounge.xml의 fab(ic_add_white_24dp) 미러
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreatePost,
                backgroundColor = sg.accent,
                contentColor = sg.onAccent
            ) {
                Icon(Icons.Default.Add, contentDescription = "글쓰기")
            }
        },
        // 당겨서 새로고침 — 글쓰기 복귀와 같은 Refresh 경로(VM Event → lazyPagingItems.refresh())를 탄다.
        // 스피너는 데이터가 이미 있는 갱신에만 돈다 — 첫 로드는 목록 중앙 스피너가 담당
        isRefreshing = lazyPagingItems.itemCount > 0 && refreshState is LoadStateLoading,
        onRefresh = { viewModel.onAction(HomeViewModel.Action.Refresh) },
        header = { listState ->
            Image(
                painter = painterResource(Res.drawable.header),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().collapsingParallax(listState)
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
        },
        modifier = modifier
    ) {
        val appendState = lazyPagingItems.loadState.append

        when {
            lazyPagingItems.itemCount == 0 && refreshState is LoadStateLoading -> item(key = "feed-loading") {
                Box(
                    Modifier.fillParentMaxWidth().padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = sg.accent)
                }
            }
            lazyPagingItems.itemCount == 0 && refreshState is LoadStateError -> item(key = "feed-error") {
                Column(
                    modifier = Modifier.fillParentMaxWidth().padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        refreshState.error.message ?: "피드를 불러오지 못했습니다.",
                        style = SgTheme.typography.bodyMedium,
                        color = sg.rust
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = lazyPagingItems::retry) {
                        Text("다시 시도", color = sg.accent)
                    }
                }
            }
            lazyPagingItems.itemCount == 0 -> item(key = "feed-empty") {
                SgEmptyState(
                    title = "아직 이야기가 없습니다",
                    subtitle = "첫 이야기를 남겨보세요.",
                    modifier = Modifier.fillParentMaxWidth().padding(vertical = 48.dp)
                )
            }
            else -> {
                items(count = lazyPagingItems.itemCount, key = lazyPagingItems.itemKey(Post::id)) { index ->
                    lazyPagingItems[index]?.let { post ->
                        SgPostCard(post, Modifier.padding(horizontal = 16.dp)) {
                            onOpenPostDetail(post.groupId, post.id)
                        }
                    }
                }
                if (appendState is LoadStateLoading || appendState is LoadStateError) {
                    item(key = "feed-footer") {
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
