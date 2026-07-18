package kr.hhp227.storygroup.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kr.hhp227.storygroup.shared.domain.model.Post
import kr.hhp227.storygroup.ui.components.SgAvatar
import kr.hhp227.storygroup.ui.components.SgCard
import kr.hhp227.storygroup.ui.components.SgEmptyState
import kr.hhp227.storygroup.ui.theme.SgTheme
import kr.hhp227.storygroup.ui.util.formatRelativeTime

/** 홈(라운지) 피드 — 웹 메인 피드 미러. iosApp HomeView.swift와 1:1 미러 */
@Composable
fun HomeScreen(viewModel: HomeViewModel, modifier: Modifier = Modifier) {
    val uiState by viewModel.uiState.collectAsState()
    val sg = SgTheme.colors

    when {
        uiState.posts.isEmpty() && uiState.isLoading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = sg.accent)
        }
        uiState.posts.isEmpty() && uiState.error != null -> Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(uiState.error.orEmpty(), style = SgTheme.typography.bodyMedium, color = sg.rust)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { viewModel.onAction(HomeViewModel.Action.Refresh) }) {
                Text("다시 시도", color = sg.accent)
            }
        }
        uiState.posts.isEmpty() -> SgEmptyState(
            title = "아직 이야기가 없습니다",
            subtitle = "첫 이야기를 남겨보세요.",
            modifier = modifier
        )
        else -> LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(uiState.posts, key = Post::id) { post ->
                FeedPostCard(post)
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
