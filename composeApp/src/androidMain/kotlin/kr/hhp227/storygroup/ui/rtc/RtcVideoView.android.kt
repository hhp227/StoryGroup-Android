package kr.hhp227.storygroup.ui.rtc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

/**
 * SurfaceViewRenderer 타일 — 트랙이 바뀌면 key로 렌더러를 새로 만든다(sink 부착이 factory 1회라 단순).
 * 세션 폐기와 뷰 해제가 경합할 수 있어 트랙/렌더러 조작은 전부 runCatching으로 감싼다
 * (VM이 상태에서 핸들을 먼저 걷어내지만 컴포지션 반영은 비동기다).
 */
@Composable
actual fun RtcVideoView(track: RtcVideoTrackHandle?, modifier: Modifier) {
    val handle = track as? AndroidRtcVideoTrackHandle ?: return

    key(handle) {
        AndroidView(
            factory = { context ->
                SurfaceViewRenderer(context).apply {
                    runCatching {
                        init(handle.eglContext, null)
                        setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                        setEnableHardwareScaler(true)
                        setMirror(handle.mirror)
                        handle.track.addSink(this)
                    }
                }
            },
            onRelease = { renderer ->
                runCatching { handle.track.removeSink(renderer) }
                runCatching { renderer.release() }
            },
            modifier = modifier
        )
    }
}
