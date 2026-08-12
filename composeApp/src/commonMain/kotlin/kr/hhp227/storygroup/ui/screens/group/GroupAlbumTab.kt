package kr.hhp227.storygroup.ui.screens.group

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.cash.paging.LoadStateError
import app.cash.paging.LoadStateLoading
import app.cash.paging.compose.LazyPagingItems
import coil3.compose.AsyncImage
import kr.hhp227.storygroup.shared.domain.model.GroupPhoto
import kr.hhp227.storygroup.shared.domain.model.GroupPhotoMediaType
import kr.hhp227.storygroup.ui.components.SgEmptyState
import kr.hhp227.storygroup.ui.components.SgPagingFooter
import kr.hhp227.storygroup.ui.theme.SgTheme
import kr.hhp227.storygroup.ui.util.rememberVideoFrame

/**
 * 앨범 탭 — 그룹 게시글 첨부(사진/동영상)의 파생 뷰(웹 /groups/[id]/photos·레거시 AlbumFragment 미러).
 * 월별 섹션 헤더 + 3열 정사각 그리드. 셀 탭 → 원본 게시글 상세(맥락 보존, 라이트박스 없음).
 * 목록이 최신 게시글 순이라 월 경계는 순서대로 끊기만 하면 된다(웹 monthLabel 미러).
 * iosApp GroupAlbumTab.swift와 1:1 미러.
 */
@Composable
internal fun GroupAlbumTab(
    lazyPagingItems: LazyPagingItems<GroupPhoto>,
    onOpenPostDetail: (postId: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors
    val refreshState = lazyPagingItems.loadState.refresh
    val appendState = lazyPagingItems.loadState.append

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        when {
            lazyPagingItems.itemCount == 0 && refreshState is LoadStateLoading -> item(
                key = "album-loading",
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
                key = "album-error",
                span = { GridItemSpan(maxLineSpan) }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        refreshState.error.message ?: "사진을 불러오지 못했습니다.",
                        style = SgTheme.typography.bodyMedium,
                        color = sg.rust
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = lazyPagingItems::retry) {
                        Text("다시 시도", color = sg.accent)
                    }
                }
            }
            lazyPagingItems.itemCount == 0 -> item(key = "album-empty", span = { GridItemSpan(maxLineSpan) }) {
                SgEmptyState(
                    title = "아직 사진이 없습니다",
                    subtitle = "게시글에 사진이나 동영상을 올리면 여기에 모여요.",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp)
                )
            }
            else -> {
                // Paging 그리드에 가변 위치 헤더를 끼우려면 items(count) 대신 개별 item()으로 펼친다.
                // 스냅샷은 월 경계 계산 전용 — 로드 트리거는 셀 안의 lazyPagingItems[index]가 담당한다
                // (스냅샷만 쓰면 위치 힌트가 없어 무한 스크롤이 죽는다).
                val snapshot = lazyPagingItems.itemSnapshotList.items
                var lastMonth: String? = null
                snapshot.forEachIndexed { index, photo ->
                    val month = monthLabel(photo.createdAt)
                    if (month != lastMonth) {
                        lastMonth = month
                        item(key = "month-$month", span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                month,
                                style = SgTheme.typography.titleSmall,
                                color = sg.inkSoft,
                                modifier = Modifier.padding(top = if (index == 0) 0.dp else 12.dp, bottom = 4.dp)
                            )
                        }
                    }
                    item(key = "photo-${photo.id}") {
                        val loaded = lazyPagingItems[index] ?: photo
                        AlbumCell(photo = loaded, onClick = { onOpenPostDetail(loaded.postId) })
                    }
                }
                if (appendState is LoadStateLoading || appendState is LoadStateError) {
                    item(key = "album-footer", span = { GridItemSpan(maxLineSpan) }) {
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

/** "2026-08-03T…" → "2026년 8월" — 서버 ISO-8601 원문에서 잘라 만든다(웹 monthLabel 미러) */
private fun monthLabel(createdAt: String): String {
    val year = createdAt.take(4)
    val month = createdAt.drop(5).take(2).trimStart('0')
    return "${year}년 ${month}월"
}

/**
 * 그리드 정사각 칸 하나 — 사진/동영상/GIF를 종류에 맞게 그린다(웹 MediaThumb 미러).
 * 동영상: 서버 썸네일이 없어 첫 프레임(rememberVideoFrame)을 포스터로 쓰고 ▶로 구분
 * (Desktop은 디코더가 없어 검은 칸+▶ 폴백 — 기존 한계 그대로).
 * GIF: 그리드에선 뱃지만 단다 — 재생은 게시글 상세 몫.
 */
@Composable
private fun AlbumCell(photo: GroupPhoto, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val sg = SgTheme.colors

    Box(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(sg.linen)
            .clickable(onClick = onClick)
    ) {
        when (photo.mediaType) {
            GroupPhotoMediaType.IMAGE -> {
                AsyncImage(
                    model = photo.image,
                    contentDescription = "${photo.authorName}의 사진",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
                // URL 저장 규칙상 확장자가 보존되므로(UUID.확장자) GIF는 경로 끝으로 판별한다(웹 미러)
                if (photo.image.substringBefore('?').substringBefore('#').lowercase().endsWith(".gif")) {
                    Text(
                        "GIF",
                        style = SgTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
            GroupPhotoMediaType.VIDEO -> {
                val frame = rememberVideoFrame(photo.image)
                if (frame != null) {
                    Image(
                        bitmap = frame,
                        contentDescription = "${photo.authorName}의 동영상",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                } else {
                    Box(Modifier.matchParentSize().background(Color.Black))
                }
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(34.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
