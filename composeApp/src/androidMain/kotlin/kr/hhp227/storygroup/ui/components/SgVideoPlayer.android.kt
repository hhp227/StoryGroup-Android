package kr.hhp227.storygroup.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * ExoPlayer + PlayerView — 컨트롤바·시크·종횡비 맞춤을 PlayerView가 다 해준다.
 * 서버 동영상은 프로그레시브 다운로드(HLS 아님)라 MediaItem.fromUri 하나로 충분하다.
 */
@Composable
actual fun SgVideoPlayer(url: String, modifier: Modifier) {
    val context = LocalContext.current
    // url이 바뀌면 다른 동영상이므로 재생기를 새로 만든다(같은 인스턴스를 재사용하면 이전 버퍼가 남는다)
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            // 자리 표시를 눌러야 이 컴포저블이 들어오므로, 들어온 시점엔 이미 사용자가 재생을 요청한 상태다
            playWhenReady = true
        }
    }

    // 화면을 벗어나거나 다른 동영상으로 넘어가면 반드시 해제한다 — 안 하면 소리가 남고 디코더가 샌다
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                this.player = player
                useController = true
                // 세로 영상이 잘리지 않게 상자 안에 맞춘다(남는 공간은 PlayerView 기본 배경인 검정)
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        // 컴포지션을 벗어날 때 View가 해제된 player를 계속 붙들고 있지 않게 끊어준다
        onRelease = { it.player = null },
        modifier = modifier
    )
}
