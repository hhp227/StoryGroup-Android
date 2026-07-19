package kr.hhp227.storygroup.ui.screens.group

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.cash.paging.LoadStateError
import app.cash.paging.LoadStateLoading
import app.cash.paging.compose.collectAsLazyPagingItems
import app.cash.paging.compose.itemKey
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kr.hhp227.storygroup.di.LocalAppContainer
import kr.hhp227.storygroup.shared.domain.model.GroupMember
import kr.hhp227.storygroup.shared.domain.model.Post
import kr.hhp227.storygroup.ui.components.SgAvatar
import kr.hhp227.storygroup.ui.components.SgCollapsingHeaderScaffold
import kr.hhp227.storygroup.ui.components.SgEmptyState
import kr.hhp227.storygroup.ui.components.SgPagingFooter
import kr.hhp227.storygroup.ui.components.SgPostCard
import kr.hhp227.storygroup.ui.components.collapsingParallax
import kr.hhp227.storygroup.ui.theme.SgTheme

@Composable
private fun groupDetailViewModel(groupId: Long): GroupDetailViewModel {
    val container = LocalAppContainer.current

    return viewModel(key = "group-detail-$groupId") {
        GroupDetailViewModel(
            groupId = groupId,
            getGroupUseCase = container.getGroupUseCase,
            getGroupMembersUseCase = container.getGroupMembersUseCase,
            getGroupPostsPagingDataUseCase = container.getGroupPostsPagingDataUseCase
        )
    }
}

/**
 * 그룹 상세 — 웹 /groups/[id] 미러: 커버 배너(그라데이션 폴백+이름/설명/역할 칩)+멤버 스트립+피드.
 * 커버는 레거시 fragment_group_detail.xml처럼 콜랩싱(SgCollapsingHeaderScaffold).
 * 계층은 iosApp GroupDetailView.swift와 1:1 미러 — Screen=상태 소유(VM 선언), Content=구독+UI.
 */
@Composable
fun GroupDetailScreen(
    groupId: Long,
    onBack: () -> Unit,
    onCreatePost: () -> Unit,
    refreshRequested: Boolean,
    onRefreshHandled: () -> Unit,
    modifier: Modifier = Modifier,
    // 라우트(백스택 엔트리) 스코프 — pop되면 함께 정리된다(ConCafe CafeScreen 패턴)
    viewModel: GroupDetailViewModel = groupDetailViewModel(groupId)
) {
    GroupDetailContent(
        viewModel = viewModel,
        onBack = onBack,
        onCreatePost = onCreatePost,
        refreshRequested = refreshRequested,
        onRefreshHandled = onRefreshHandled,
        modifier = modifier
    )
}

@Composable
private fun GroupDetailContent(
    viewModel: GroupDetailViewModel,
    onBack: () -> Unit,
    onCreatePost: () -> Unit,
    refreshRequested: Boolean,
    onRefreshHandled: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    // 상태에서 pagingData만 뽑아낸 스트림을 수집 — Paging-CRUD 샘플·iOS($state.map)와 동일 관용구
    val pagingDataFlow = remember(viewModel) {
        viewModel.uiState.map { it.pagingData }.distinctUntilChanged()
    }
    val lazyPagingItems = pagingDataFlow.collectAsLazyPagingItems()
    val sg = SgTheme.colors

    // 상세 진입 시 신선화 — VM이 탭 전환에도 유지되므로 재진입 때도 최신화된다
    LaunchedEffect(viewModel) {
        viewModel.onAction(GroupDetailViewModel.Action.Refresh)
    }
    // 작성 화면에서 돌아온 결과 — 피드를 첫 페이지부터 다시 읽는다(Paging-CRUD 샘플 미러)
    LaunchedEffect(refreshRequested) {
        if (refreshRequested) {
            lazyPagingItems.refresh()
            onRefreshHandled()
        }
    }
    SgCollapsingHeaderScaffold(
        // 로드 전엔 빈 제목 — 커버 그라데이션(groupId 기반)은 즉시 그려진다
        title = uiState.group?.name.orEmpty(),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
        },
        // 레거시 fragment_group_detail.xml의 fab 미러
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreatePost,
                backgroundColor = sg.accent,
                contentColor = sg.onAccent
            ) {
                Icon(Icons.Default.Add, contentDescription = "글쓰기")
            }
        },
        header = { listState ->
            // 커버 이미지 로딩(④) 전까지 웹 GroupCover 그라데이션 폴백 — 콘텐츠 전체가 패럴럭스로 접힌다
            Box(
                Modifier
                    .matchParentSize()
                    .collapsingParallax(listState)
                    .background(groupCoverBrush(viewModel.groupId, sg))
            )
            // 웹 커버 하단 스크림(0.05→0.62) 위 그룹명/설명/역할 칩 미러
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        0.35f to Color.Black.copy(alpha = 0.05f),
                        1f to Color.Black.copy(alpha = 0.62f)
                    )
                )
            )
            uiState.group?.let { group ->
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            group.name,
                            style = SgTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(8.dp))
                        RoleChip(group.myRole)
                    }
                    if (!group.description.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            group.description.orEmpty(),
                            style = SgTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.88f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        modifier = modifier
    ) {
        // 그룹/멤버는 UiState, 피드는 Paging3 LoadState — 다음 페이지 트리거는 prefetchDistance가 담당
        val refreshState = lazyPagingItems.loadState.refresh
        val appendState = lazyPagingItems.loadState.append

        if (uiState.members.isNotEmpty()) {
            item(key = "members") {
                MemberStrip(uiState.members, Modifier.padding(horizontal = 16.dp))
            }
        }
        if (uiState.error != null) {
            item(key = "detail-error") {
                Column(
                    modifier = Modifier.fillParentMaxWidth().padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(uiState.error.orEmpty(), style = SgTheme.typography.bodyMedium, color = sg.rust)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.onAction(GroupDetailViewModel.Action.Refresh) }) {
                        Text("다시 시도", color = sg.accent)
                    }
                }
            }
        }
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
                        SgPostCard(post, Modifier.padding(horizontal = 16.dp))
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

/** 웹 사이드바 MemberPanel의 앱 변형 — 수평 아바타 스트립 */
@Composable
private fun MemberStrip(members: List<GroupMember>, modifier: Modifier = Modifier) {
    val sg = SgTheme.colors

    Column(modifier) {
        Text("멤버 ${members.size}", style = SgTheme.typography.titleSmall, color = sg.ink)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(members, key = GroupMember::userId) { member ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SgAvatar(member.name)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        member.name,
                        style = SgTheme.typography.labelSmall,
                        color = sg.inkSoft,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
