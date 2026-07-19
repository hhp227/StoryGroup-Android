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
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kr.hhp227.storygroup.shared.domain.model.GroupMember
import kr.hhp227.storygroup.shared.domain.model.Post
import kr.hhp227.storygroup.ui.components.SgAvatar
import kr.hhp227.storygroup.ui.components.SgCollapsingHeaderScaffold
import kr.hhp227.storygroup.ui.components.SgEmptyState
import kr.hhp227.storygroup.ui.components.SgPagingFooter
import kr.hhp227.storygroup.ui.components.SgPostCard
import kr.hhp227.storygroup.ui.components.collapsingParallax
import kr.hhp227.storygroup.ui.theme.SgTheme

/**
 * 그룹 상세 — 웹 /groups/[id] 미러: 커버 배너(그라데이션 폴백+이름/설명/역할 칩)+멤버 스트립+피드.
 * 커버는 레거시 fragment_group_detail.xml처럼 콜랩싱(SgCollapsingHeaderScaffold).
 * iosApp GroupDetailView.swift와 1:1 미러
 */
@Composable
fun GroupDetailScreen(
    viewModel: GroupDetailViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val sg = SgTheme.colors

    // 상세 진입 시 신선화 — VM이 탭 전환에도 유지되므로 재진입 때도 최신화된다
    LaunchedEffect(viewModel) {
        viewModel.onAction(GroupDetailViewModel.Action.Refresh)
    }
    SgCollapsingHeaderScaffold(
        title = uiState.group.name,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
        },
        header = { listState ->
            // 커버 이미지 로딩(④) 전까지 웹 GroupCover 그라데이션 폴백 — 콘텐츠 전체가 패럴럭스로 접힌다
            Box(
                Modifier
                    .matchParentSize()
                    .collapsingParallax(listState)
                    .background(groupCoverBrush(uiState.group.id, sg))
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
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        uiState.group.name,
                        style = SgTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(8.dp))
                    RoleChip(uiState.group.myRole)
                }
                if (!uiState.group.description.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        uiState.group.description.orEmpty(),
                        style = SgTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.88f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        modifier = modifier
    ) {
        when {
            uiState.posts.isEmpty() && uiState.members.isEmpty() && uiState.isLoading ->
                item(key = "detail-loading") {
                    Box(
                        Modifier.fillParentMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = sg.accent)
                    }
                }
            uiState.posts.isEmpty() && uiState.members.isEmpty() && uiState.error != null ->
                item(key = "detail-error") {
                    Column(
                        modifier = Modifier.fillParentMaxWidth().padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(uiState.error.orEmpty(), style = SgTheme.typography.bodyMedium, color = sg.rust)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { viewModel.onAction(GroupDetailViewModel.Action.Refresh) }) {
                            Text("다시 시도", color = sg.accent)
                        }
                    }
                }
            else -> {
                if (uiState.members.isNotEmpty()) {
                    item(key = "members") {
                        MemberStrip(uiState.members, Modifier.padding(horizontal = 16.dp))
                    }
                }
                if (uiState.posts.isEmpty() && !uiState.isLoading) {
                    item(key = "detail-empty") {
                        SgEmptyState(
                            title = "아직 이야기가 없습니다",
                            subtitle = "첫 이야기를 남겨보세요.",
                            modifier = Modifier.fillParentMaxWidth().padding(vertical = 48.dp)
                        )
                    }
                } else {
                    items(uiState.posts, key = Post::id) { post ->
                        SgPostCard(post, Modifier.padding(horizontal = 16.dp))
                    }
                    if (uiState.hasMore || uiState.isLoadingMore || uiState.error != null) {
                        item(key = "detail-footer") {
                            SgPagingFooter(
                                isLoadingMore = uiState.isLoadingMore,
                                error = uiState.error,
                                postCount = uiState.posts.size,
                                onLoadMore = { viewModel.onAction(GroupDetailViewModel.Action.LoadMore) }
                            )
                        }
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
