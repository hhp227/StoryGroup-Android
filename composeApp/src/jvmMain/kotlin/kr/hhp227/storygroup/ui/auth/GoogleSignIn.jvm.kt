package kr.hhp227.storygroup.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kr.hhp227.storygroup.shared.config.GoogleAuthConfig
import java.awt.Desktop
import java.net.URI

/** Desktop — 시스템 브라우저 + 루프백 PKCE(LoopbackOAuth). Desktop 클라이언트 ID 미설정이면 버튼 숨김 */
@Composable
actual fun rememberGoogleSignInLauncher(): GoogleSignInLauncher = remember {
    object : GoogleSignInLauncher {
        override val isAvailable: Boolean =
            GoogleAuthConfig.DESKTOP_CLIENT_ID.isNotEmpty() && Desktop.isDesktopSupported()

        override suspend fun signIn(): GoogleCredential? = withContext(Dispatchers.IO) {
            val oauth = LoopbackOAuth(GoogleAuthConfig.DESKTOP_CLIENT_ID) { url -> Desktop.getDesktop().browse(URI(url)) }
            try {
                oauth.authorize()
            } catch (e: TimeoutCancellationException) {
                // 취소 예외로 새면 호출 코루틴이 조용히 죽는다 — 일반 실패로 바꿔 화면에 안내
                throw IllegalStateException("구글 로그인 시간이 초과됐습니다. 다시 시도해주세요.")
            }
        }
    }
}
