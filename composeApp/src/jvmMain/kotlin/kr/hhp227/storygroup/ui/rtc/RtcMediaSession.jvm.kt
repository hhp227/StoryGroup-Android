package kr.hhp227.storygroup.ui.rtc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kr.hhp227.storygroup.shared.domain.model.IceServer

/** Desktop은 WebRTC 미디어 미지원 — null 팩토리라 통화가 로스터 전용으로 동작한다 */
@Composable
actual fun rememberRtcMediaSessionFactory(): RtcMediaSessionFactory = remember {
    object : RtcMediaSessionFactory {
        override fun create(iceServers: List<IceServer>): RtcMediaSession? = null
    }
}

/** Desktop은 권한 개념이 없다 — 즉시 허용(미디어는 어차피 팩토리가 null이라 붙지 않는다) */
@Composable
actual fun rememberRtcPermissionsRequester(onResult: (granted: Boolean) -> Unit): () -> Unit {
    val currentOnResult by rememberUpdatedState(onResult)

    return { currentOnResult(true) }
}
