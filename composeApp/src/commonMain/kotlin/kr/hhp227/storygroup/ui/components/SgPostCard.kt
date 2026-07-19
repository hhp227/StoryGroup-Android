package kr.hhp227.storygroup.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kr.hhp227.storygroup.shared.domain.model.Post
import kr.hhp227.storygroup.ui.theme.SgTheme
import kr.hhp227.storygroup.ui.util.formatRelativeTime

/** 게시글 피드 카드 — 웹 피드 카드 미러(홈 라운지/그룹 상세 공유). 첨부는 요약 표기(이미지 로딩은 ④ 몫) */
@Composable
fun SgPostCard(post: Post, modifier: Modifier = Modifier) {
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

/** 웹 sentinel 미러 — 푸터가 화면에 들어오면 다음 페이지를 읽고, 실패 시엔 수동 재시도만 노출 */
@Composable
fun SgPagingFooter(
    isLoadingMore: Boolean,
    error: String?,
    postCount: Int,
    onLoadMore: () -> Unit,
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
