package kr.hhp227.storygroup.ui.rtc

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * WebRTC 비디오 타일 — Android는 SurfaceViewRenderer, Desktop은 미지원 자리 표시.
 * track이 null이면 아무것도 그리지 않는다(호출 측이 아바타 폴백을 겹쳐 그린다).
 */
@Composable
expect fun RtcVideoView(track: RtcVideoTrackHandle?, modifier: Modifier = Modifier)
