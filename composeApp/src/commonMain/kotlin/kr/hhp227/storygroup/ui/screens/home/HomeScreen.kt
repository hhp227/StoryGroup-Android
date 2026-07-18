package kr.hhp227.storygroup.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kr.hhp227.storygroup.ui.components.SgAvatar
import kr.hhp227.storygroup.ui.components.SgCard
import kr.hhp227.storygroup.ui.theme.SgTheme

/** 피드 API 연동 전 표시용 모델 — TODO: shared에 FeedRepository/HomeViewModel 추가 후 교체 */
data class FeedPostUiModel(
    val id: Long,
    val authorName: String,
    val groupName: String?,
    val timeAgo: String,
    val content: String,
    val likeCount: Int,
    val commentCount: Int
)

private val samplePosts = listOf(
    FeedPostUiModel(1, "홍희표", "우리들의 이야기", "방금", "라운지 피드 자리입니다. 홈 피드 API 연동 후 실제 게시글이 표시됩니다.", 3, 1),
    FeedPostUiModel(2, "김재환", "등산 모임", "10분 전", "이번 주말 정기 모임 사진 올렸습니다. 앨범에서 확인해주세요!", 5, 2),
    FeedPostUiModel(3, "이수진", null, "1시간 전", "라운지에 처음 글 써봐요. 다들 반갑습니다 :)", 8, 4)
)

/** 홈(라운지) 피드 — 웹 메인 피드 미러 */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(samplePosts, key = { it.id }) { post ->
            FeedPostCard(post)
        }
    }
}

@Composable
private fun FeedPostCard(post: FeedPostUiModel, modifier: Modifier = Modifier) {
    val sg = SgTheme.colors

    SgCard(modifier = modifier.fillMaxWidth(), onClick = { /* TODO: 게시글 상세 */ }) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SgAvatar(post.authorName)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(post.authorName, style = SgTheme.typography.titleSmall, color = sg.ink)
                    Row {
                        if (post.groupName != null) {
                            Text(post.groupName, style = SgTheme.typography.bodySmall, color = sg.accent)
                            Text(" · ", style = SgTheme.typography.bodySmall, color = sg.inkFaint)
                        }
                        Text(post.timeAgo, style = SgTheme.typography.bodySmall, color = sg.inkFaint)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(post.content, style = SgTheme.typography.bodyMedium, color = sg.ink)
            Spacer(Modifier.height(12.dp))
            Row {
                Text("좋아요 ${post.likeCount}", style = SgTheme.typography.bodySmall, color = sg.inkSoft)
                Spacer(Modifier.width(16.dp))
                Text("댓글 ${post.commentCount}", style = SgTheme.typography.bodySmall, color = sg.inkSoft)
            }
        }
    }
}
