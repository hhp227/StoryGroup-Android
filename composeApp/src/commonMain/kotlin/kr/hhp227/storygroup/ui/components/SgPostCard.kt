package kr.hhp227.storygroup.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kr.hhp227.storygroup.shared.domain.model.Post
import kr.hhp227.storygroup.ui.theme.SgTheme
import kr.hhp227.storygroup.ui.util.formatRelativeTime
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import storygroup.composeapp.generated.resources.Res
import storygroup.composeapp.generated.resources.common_retry
import storygroup.composeapp.generated.resources.comments_n
import storygroup.composeapp.generated.resources.likes_n
import storygroup.composeapp.generated.resources.post_comment
import storygroup.composeapp.generated.resources.post_like
import storygroup.composeapp.generated.resources.post_notice_badge
import storygroup.composeapp.generated.resources.post_share
import storygroup.composeapp.generated.resources.post_unlike

/** 미디어 그리드에 보여줄 최대 장수 — 넘치면 마지막 타일에 "+N"(전체는 상세에서) */
private const val MEDIA_GRID_MAX = 6

/**
 * 게시글 피드 카드 — 웹 피드 카드 미러(홈 라운지/그룹 상세 공유). 미디어는 카드 전폭 풀블리드 —
 * 1개면 원본 비율 한 장(레거시 iv_post 미러), 2개 이상이면 2열 스태거드 그리드.
 * 동영상은 ▶ 자리로 표시한다(재생은 상세에서).
 * onClick을 주지 않으면 카드는 눌러도 아무 일이 없다 — 상세로 갈 수 없는 자리(미리보기 등)를 위해 기본값을 둔다.
 */
@Composable
fun SgPostCard(
    post: Post,
    modifier: Modifier = Modifier,
    onToggleLike: () -> Unit = {},
    onShare: () -> Unit = {},
    onClick: () -> Unit = {}
) {
    val sg = SgTheme.colors

    SgCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Column {
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
                            stringResource(Res.string.post_notice_badge),
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
            }
            // 미디어는 패딩 밖 카드 전폭 — 레거시 iv_post(match_parent+adjustViewBounds) 풀블리드 미러.
            // 카드 안에서는 재생하지 않는다(카드 전체가 상세로 가는 링크라 탭이 겹친다).
            PostCardMediaBlock(imageUrls = post.imageUrls, videoUrls = post.videoUrls)
            // 레거시 item_post.xml 미러 — 전폭 구분선 + 등분 3버튼(좋아요/댓글/공유)
            Divider(color = sg.stoneBorder)
            Row {
                SgPostCardAction(
                    label = if (post.likeCount > 0) pluralStringResource(Res.plurals.likes_n, post.likeCount, post.likeCount) else stringResource(Res.string.post_like),
                    icon = if (post.likedByMe) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    iconDescription = if (post.likedByMe) stringResource(Res.string.post_unlike) else stringResource(Res.string.post_like),
                    tint = if (post.likedByMe) sg.accent else sg.inkSoft,
                    onClick = onToggleLike,
                    modifier = Modifier.weight(1f)
                )
                SgPostCardAction(
                    label = if (post.replyCount > 0) pluralStringResource(Res.plurals.comments_n, post.replyCount, post.replyCount) else stringResource(Res.string.post_comment),
                    icon = null,
                    iconDescription = null,
                    tint = sg.inkSoft,
                    onClick = onClick,
                    modifier = Modifier.weight(1f)
                )
                SgPostCardAction(
                    label = stringResource(Res.string.post_share),
                    icon = Icons.Default.Share,
                    iconDescription = stringResource(Res.string.post_share),
                    tint = sg.inkSoft,
                    onClick = onShare,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private data class PostCardMedia(val url: String, val isVideo: Boolean)

/**
 * 카드 전폭 미디어 블록 — 이미지들 뒤에 동영상들(합산 기준). 1개=풀블리드 원본 비율,
 * 2~[MEDIA_GRID_MAX]개=2열 스태거드(타일 간 2dp). 서버에 크기 메타데이터가 없어
 * 열 배분은 인덱스 교대(0·2·4→왼쪽) — 결정적이라 웹·iOS와 항상 같은 모양이다.
 */
@Composable
private fun PostCardMediaBlock(imageUrls: List<String>, videoUrls: List<String>, modifier: Modifier = Modifier) {
    val media = imageUrls.map { PostCardMedia(it, isVideo = false) } + videoUrls.map { PostCardMedia(it, isVideo = true) }

    when {
        media.isEmpty() -> Unit
        media.size == 1 -> PostCardMediaTile(media[0], overflowCount = 0, modifier = modifier.fillMaxWidth())
        else -> {
            val visible = media.take(MEDIA_GRID_MAX)
            val overflow = media.size - visible.size

            Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (column in 0..1) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        visible.forEachIndexed { index, item ->
                            if (index % 2 == column) {
                                PostCardMediaTile(
                                    media = item,
                                    overflowCount = if (index == visible.lastIndex) overflow else 0,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 미디어 한 타일 — 폭 맞춤+원본 비율(동영상은 프레임 비율, 없으면 16:9). [overflowCount]>0이면 "+N" 오버레이 */
@Composable
private fun PostCardMediaTile(media: PostCardMedia, overflowCount: Int, modifier: Modifier = Modifier) {
    Box(modifier) {
        if (media.isVideo) {
            SgVideoTile(media.url, modifier = Modifier.fillMaxWidth())
        } else {
            AsyncImage(
                model = media.url,
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (overflowCount > 0) {
            Box(
                Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Text("+$overflowCount", style = SgTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** 액션 바 한 칸 — 카드 onClick 위에서 자체 clickable이 탭을 가로채므로 중복 반응이 없다 */
@Composable
private fun SgPostCardAction(
    label: String,
    icon: ImageVector?,
    iconDescription: String?,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = iconDescription, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(label, style = SgTheme.typography.bodySmall, color = tint)
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
                    Text(stringResource(Res.string.common_retry), color = sg.accent)
                }
            }
            isLoadingMore -> CircularProgressIndicator(color = sg.accent, modifier = Modifier.padding(8.dp))
        }
    }
}
