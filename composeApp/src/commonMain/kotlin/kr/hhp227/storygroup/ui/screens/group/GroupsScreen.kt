package kr.hhp227.storygroup.ui.screens.group

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cash.paging.LoadStateError
import app.cash.paging.LoadStateLoading
import app.cash.paging.compose.collectAsLazyPagingItems
import app.cash.paging.compose.itemKey
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kr.hhp227.storygroup.di.sessionViewModel
import kr.hhp227.storygroup.shared.domain.model.Group
import kr.hhp227.storygroup.shared.domain.model.GroupRole
import kr.hhp227.storygroup.ui.components.SgBellAction
import kr.hhp227.storygroup.ui.components.SgEmptyState
import kr.hhp227.storygroup.ui.components.SgPagingFooter
import kr.hhp227.storygroup.ui.components.SgPullRefreshBox
import kr.hhp227.storygroup.ui.components.SgTopBar
import kr.hhp227.storygroup.ui.screens.notification.sessionNotificationsViewModel
import kr.hhp227.storygroup.ui.theme.SgColors
import kr.hhp227.storygroup.ui.theme.SgTheme

/**
 * 가입중인 그룹 목록 + 만들기/찾기 진입 — 웹 /groups 내 그룹 탭 미러(라운지 제외).
 * 상세는 App의 NavHost 풀스크린 목적지(onOpenGroup) — iosApp NavigationStack push 미러
 */
@Composable
fun GroupsScreen(
    onOpenGroup: (Group) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenCreateGroup: () -> Unit,
    onOpenDiscoverGroups: () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    viewModel: GroupsViewModel = sessionViewModel { GroupsViewModel(it.getMyGroupsPagingDataUseCase) }
) {
    GroupsContent(
        viewModel = viewModel,
        onOpenGroup = onOpenGroup,
        onOpenNotifications = onOpenNotifications,
        onOpenCreateGroup = onOpenCreateGroup,
        onOpenDiscoverGroups = onOpenDiscoverGroups,
        navigationIcon = navigationIcon,
        modifier = modifier
    )
}

@Composable
private fun GroupsContent(
    viewModel: GroupsViewModel,
    onOpenGroup: (Group) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenCreateGroup: () -> Unit,
    onOpenDiscoverGroups: () -> Unit,
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

    // VM의 일회성 갱신 이벤트 — 프레젠터 refresh()가 활성 PagingSource를 무효화해
    // 같은 스트림이 새 세대(첫 페이지)를 방출한다(홈 피드와 동일 패턴)
    LaunchedEffect(Unit) {
        viewModel.event.collect { event ->
            when (event) {
                GroupsViewModel.Event.Refresh -> lazyPagingItems.refresh()
            }
        }
    }
    // 그룹 탭은 상세(콜랩싱 헤더)와의 전환 때문에 셸이 아닌 화면이 상단바를 소유한다(홈과 동일)
    Column(modifier) {
        SgTopBar(
            title = "그룹",
            navigationIcon = navigationIcon,
            actions = {
                SgBellAction(
                    unreadCount = notificationsUiState.unreadCount,
                    onClick = onOpenNotifications
                )
            }
        )
        // 당겨서 새로고침 — 그룹 생성/가입 복귀와 같은 Refresh 경로(VM Event → lazyPagingItems.refresh())를 탄다.
        // 스피너는 데이터가 이미 있는 갱신에만 돈다 — 첫 로드는 목록 중앙 스피너가 담당(홈과 동일)
        SgPullRefreshBox(
            refreshing = lazyPagingItems.itemCount > 0 && refreshState is LoadStateLoading,
            onRefresh = { viewModel.onAction(GroupsViewModel.Action.Refresh) },
            modifier = Modifier.fillMaxSize()
        ) {
            // 웹 /groups 내 그룹 탭(auto-fill minmax(160px,1fr) CSS 그리드)·레거시 GroupFragment(GridLayoutManager
            // 2열/4열) 미러 — 그룹 찾기(목록)와 달리 내 그룹은 그리드로 보여준다
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 152.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item(key = "actions", span = { GridItemSpan(maxLineSpan) }) {
                    GroupActionsRow(onOpenCreateGroup = onOpenCreateGroup, onOpenDiscoverGroups = onOpenDiscoverGroups)
                }
                val appendState = lazyPagingItems.loadState.append

                when {
                    lazyPagingItems.itemCount == 0 && refreshState is LoadStateLoading -> item(
                        key = "groups-loading",
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
                        key = "groups-error",
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                refreshState.error.message ?: "그룹 목록을 불러오지 못했습니다.",
                                style = SgTheme.typography.bodyMedium,
                                color = sg.rust
                            )
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = lazyPagingItems::retry) {
                                Text("다시 시도", color = sg.accent)
                            }
                        }
                    }
                    lazyPagingItems.itemCount == 0 -> item(key = "groups-empty", span = { GridItemSpan(maxLineSpan) }) {
                        SgEmptyState(
                            title = "아직 그룹이 없습니다",
                            subtitle = "새 그룹을 만들거나 그룹 찾기에서 참여해보세요.",
                            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp)
                        )
                    }
                    else -> {
                        items(count = lazyPagingItems.itemCount, key = lazyPagingItems.itemKey(Group::id)) { index ->
                            lazyPagingItems[index]?.let { group ->
                                GroupCard(group, onClick = { onOpenGroup(group) })
                            }
                        }
                        if (appendState is LoadStateLoading || appendState is LoadStateError) {
                            item(key = "groups-footer", span = { GridItemSpan(maxLineSpan) }) {
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
    }
}

@Composable
private fun GroupActionsRow(onOpenCreateGroup: () -> Unit, onOpenDiscoverGroups: () -> Unit, modifier: Modifier = Modifier) {
    val sg = SgTheme.colors

    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = onOpenCreateGroup,
            modifier = Modifier.weight(1f),
            shape = SgTheme.shapes.button,
            border = BorderStroke(1.dp, sg.stoneBorder),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = sg.accent)
        ) {
            Text("그룹 만들기", fontWeight = FontWeight.Bold)
        }
        OutlinedButton(
            onClick = onOpenDiscoverGroups,
            modifier = Modifier.weight(1f),
            shape = SgTheme.shapes.button,
            border = BorderStroke(1.dp, sg.stoneBorder),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = sg.accent)
        ) {
            Text("그룹 찾기", fontWeight = FontWeight.Bold)
        }
    }
}

/** 웹 GroupCard 미러 — 정사각 커버(역할 칩 오버레이) + 이름/소개, 카드 배경 없이 그리드 타일로 */
@Composable
private fun GroupCard(group: Group, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val sg = SgTheme.colors

    Column(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(SgTheme.shapes.card)
                .let { if (group.image == null) it.background(groupCoverBrush(group.id, sg)) else it }
        ) {
            // 웹 GroupCover 미러 — group.image 있으면 실사진, 없으면 그룹별 그라데이션+이니셜 폴백
            if (group.image != null) {
                AsyncImage(
                    model = group.image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    group.name.take(1),
                    style = SgTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            if (group.myRole != GroupRole.MEMBER) {
                RoleChip(group.myRole, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
            }
        }
        Column {
            Text(
                group.name,
                style = SgTheme.typography.titleSmall,
                color = sg.ink,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!group.description.isNullOrBlank()) {
                Text(
                    group.description.orEmpty(),
                    style = SgTheme.typography.bodySmall,
                    color = sg.inkSoft,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** 웹 roleLabel 미러 */
internal fun roleLabel(role: GroupRole): String = when (role) {
    GroupRole.OWNER -> "방장"
    GroupRole.ADMIN -> "부방장"
    GroupRole.MEMBER -> "멤버"
}

/** 역할 칩 — 웹 roleChipClass 미러(방장=accent, 부방장 등=accent2) */
@Composable
internal fun RoleChip(role: GroupRole, modifier: Modifier = Modifier) {
    val sg = SgTheme.colors

    Text(
        roleLabel(role),
        style = SgTheme.typography.labelSmall,
        color = if (role == GroupRole.OWNER) sg.accent else sg.accent2,
        modifier = modifier
            .background(if (role == GroupRole.OWNER) sg.accentSoft else sg.accent2Soft, SgTheme.shapes.button)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

/** 웹 GroupCover 폴백 미러 — COVER_COLORS[id%4] → accent2 그라데이션(135deg) */
internal fun groupCoverBrush(groupId: Long, sg: SgColors): Brush {
    val base = listOf(sg.accent, sg.accent2, sg.moss, sg.amber)[(groupId % 4).toInt()]
    return Brush.linearGradient(listOf(base, sg.accent2))
}
