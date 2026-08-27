package kr.hhp227.storygroup.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kr.hhp227.storygroup.ui.util.rememberVideoFrame
import org.jetbrains.compose.resources.stringResource
import storygroup.composeapp.generated.resources.Res
import storygroup.composeapp.generated.resources.video_play

/** 첫 프레임이 오기 전(또는 Desktop처럼 못 뽑는 플랫폼)의 자리 표시 종횡비 */
private const val FALLBACK_VIDEO_ASPECT_RATIO = 16f / 9f

/**
 * 게시글 첨부 동영상 한 칸 — 평소엔 첫 프레임 위에 ▶를 얹은 자리 표시, [isPlaying]이면 그 자리에 재생기를 띄운다.
 * 상자는 가로를 꽉 채우고 세로는 동영상 실제 종횡비(첫 프레임 크기)를 따른다 — 세로 영상이 16:9 상자에서
 * 작게 letterbox 되지 않게.
 *
 * 재생 상태를 스스로 갖지 않는 이유: 한 게시글에 동영상이 여럿일 때 재생기가 여럿 뜨지 않도록
 * 호출부가 "지금 재생 중인 URL" 하나만 들고 판단하기 때문이다.
 */
@Composable
fun SgVideoAttachment(
    url: String,
    isPlaying: Boolean,
    onPlayRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 프레임을 여기서 들고 있어야 재생기로 바뀐 뒤에도 상자가 같은 비율을 유지한다
    val frame = rememberVideoFrame(url)
    val aspectRatio = frame?.let { it.width.toFloat() / it.height } ?: FALLBACK_VIDEO_ASPECT_RATIO
    val box = modifier
        .fillMaxWidth()
        .aspectRatio(aspectRatio)
        .clip(RoundedCornerShape(12.dp))

    if (isPlaying) {
        SgVideoPlayer(url, modifier = box)
    } else {
        VideoPoster(frame = frame, badgeSize = 56.dp, modifier = box.clickable(onClick = onPlayRequest))
    }
}

/**
 * 작성 폼용 풀폭 동영상 포스터 — SgVideoAttachment와 같은 실비율 상자지만 재생하지 않는다
 * (작성 중 미리보기 전용, 레거시 input_contents의 VideoView 자리 미러).
 */
@Composable
fun SgVideoPoster(url: String, modifier: Modifier = Modifier) {
    val frame = rememberVideoFrame(url)
    val aspectRatio = frame?.let { it.width.toFloat() / it.height } ?: FALLBACK_VIDEO_ASPECT_RATIO

    VideoPoster(
        frame = frame,
        badgeSize = 56.dp,
        modifier = modifier.fillMaxWidth().aspectRatio(aspectRatio).clip(RoundedCornerShape(12.dp))
    )
}

/**
 * 피드 카드 미디어 그리드용 동영상 타일 — 풀폭·실비율(프레임 없으면 16:9), 풀블리드 칸이라 모서리를 깎지 않는다.
 * 여기서는 재생하지 않는다(피드 카드는 전체가 상세로 가는 링크라 탭이 겹친다).
 */
@Composable
fun SgVideoTile(url: String, modifier: Modifier = Modifier) {
    val frame = rememberVideoFrame(url)
    val aspectRatio = frame?.let { it.width.toFloat() / it.height } ?: FALLBACK_VIDEO_ASPECT_RATIO

    VideoPoster(
        frame = frame,
        badgeSize = 36.dp,
        modifier = modifier.fillMaxWidth().aspectRatio(aspectRatio)
    )
}

/**
 * 피드 카드·작성 폼용 동영상 썸네일 — 이미지 썸네일과 같은 정사각 칸에 첫 프레임과 ▶를 얹는다.
 * 여기서는 재생하지 않는다(피드 카드는 전체가 상세로 가는 링크라 탭이 겹친다).
 */
@Composable
fun SgVideoThumbnail(url: String, size: Dp, modifier: Modifier = Modifier) {
    VideoPoster(
        frame = rememberVideoFrame(url),
        badgeSize = 36.dp,
        modifier = modifier.size(size).clip(RoundedCornerShape(12.dp))
    )
}

/**
 * 첫 프레임 + ▶ 뱃지. 프레임을 아직 못 읽었거나 플랫폼이 디코딩을 못 하면(Desktop) 검은 바탕만 깔린다 —
 * 그래도 ▶는 항상 있어서 "동영상"이라는 건 알 수 있다.
 */
@Composable
private fun VideoPoster(frame: ImageBitmap?, badgeSize: Dp, modifier: Modifier) {
    Box(modifier.background(Color.Black.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
        frame?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                // 칸을 꽉 채운다 — 세로 영상이 정사각 칸에서 letterbox로 남으면 더 알아보기 어렵다
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            Modifier.size(badgeSize).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = stringResource(Res.string.video_play), tint = Color.White)
        }
    }
}
