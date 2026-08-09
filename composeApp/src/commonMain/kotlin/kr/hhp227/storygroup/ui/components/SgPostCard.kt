package kr.hhp227.storygroup.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kr.hhp227.storygroup.shared.domain.model.Post
import kr.hhp227.storygroup.ui.theme.SgTheme
import kr.hhp227.storygroup.ui.util.formatRelativeTime

/**
 * 게시글 피드 카드 — 웹 피드 카드 미러(홈 라운지/그룹 상세 공유). 이미지는 가로 스크롤 썸네일, 동영상은 개수만 요약(재생은 후속).
 * onClick을 주지 않으면 카드는 눌러도 아무 일이 없다 — 상세로 갈 수 없는 자리(미리보기 등)를 위해 기본값을 둔다.
 */
@Composable
fun SgPostCard(post: Post, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    val sg = SgTheme.colors

    SgCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SgAvatar(post.authorName, imageUrl = post.authorProfileImg)
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
            if (post.imageUrls.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(post.imageUrls) { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(120.dp).clip(SgTheme.shapes.field)
                        )
                    }
                }
            }
            // 동영상 재생은 후속 작업 — 개수만 요약 표기(웹 GIF 뱃지 등은 다루지 않음)
            if (post.videoUrls.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("동영상 ${post.videoUrls.size}개", style = SgTheme.typography.bodySmall, color = sg.inkSoft)
            }
        }
    }
}

/** 추가 페이지 로딩/실패 표시 — 다음 페이지 트리거는 Paging3(prefetchDistance)가 담당, 실패 시 수동 재시도만 노출 */
@Composable
fun SgPagingFooter(
    isLoadingMore: Boolean,
    error: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors

    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when {
            error != null -> {
                Text(error, style = SgTheme.typography.bodySmall, color = sg.rust)
                TextButton(onClick = onRetry) {
                    Text("다시 시도", color = sg.accent)
                }
            }
            isLoadingMore -> CircularProgressIndicator(color = sg.accent, modifier = Modifier.padding(8.dp))
        }
    }
}
