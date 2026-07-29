package kr.hhp227.storygroup.ui.rtc

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Desktop 미지원 — 팩토리가 null이라 미디어 타일 자체가 그려지지 않으므로 빈 자리만 지킨다 */
@Composable
actual fun RtcVideoView(track: RtcVideoTrackHandle?, modifier: Modifier) {
    Box(modifier)
}
