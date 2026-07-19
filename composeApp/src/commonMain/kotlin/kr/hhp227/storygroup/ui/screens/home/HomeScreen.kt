package kr.hhp227.storygroup.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kr.hhp227.storygroup.shared.domain.model.Post
import kr.hhp227.storygroup.ui.components.SgCollapsingHeaderScaffold
import kr.hhp227.storygroup.ui.components.SgEmptyState
import kr.hhp227.storygroup.ui.components.SgPagingFooter
import kr.hhp227.storygroup.ui.components.SgPostCard
import kr.hhp227.storygroup.ui.components.collapsingParallax
import kr.hhp227.storygroup.ui.theme.SgTheme
import org.jetbrains.compose.resources.painterResource
import storygroup.composeapp.generated.resources.Res
import storygroup.composeapp.generated.resources.header

/**
 * 홈(라운지) 피드 — 웹 메인 피드 미러 + 레거시 CollapsingToolbar 헤더(SgCollapsingHeaderScaffold).
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

    SgCollapsingHeaderScaffold(
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
                    SgPostCard(post, Modifier.padding(horizontal = 16.dp))
                }
                if (uiState.hasMore || uiState.isLoadingMore || uiState.error != null) {
                    item(key = "feed-footer") {
                        SgPagingFooter(
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
}
