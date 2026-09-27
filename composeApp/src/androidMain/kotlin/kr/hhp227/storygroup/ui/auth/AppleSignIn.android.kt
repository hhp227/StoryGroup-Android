package kr.hhp227.storygroup.ui.auth

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kr.hhp227.storygroup.shared.config.AppleAuthConfig

/** MainActivity(singleTask)가 받은 storygroup://auth/apple 딥링크를 대기 중인 런처로 넘긴다 */
object AppleAuthRedirects {
    private val _params = MutableSharedFlow<Map<String, String>>(extraBufferCapacity = 1)
    val params: SharedFlow<Map<String, String>> = _params.asSharedFlow()

    fun handle(uri: Uri?) {
        if (uri == null || uri.scheme != AppleAuthConfig.ANDROID_REDIRECT_SCHEME || uri.host != "auth" || uri.path != "/apple") return
        _params.tryEmit(uri.queryParameterNames.associateWith { uri.getQueryParameter(it).orEmpty() })
    }
}

/**
 * 시스템 브라우저로 애플 인가 → 백엔드 콜백 → 딥링크 복귀(설계 §2.4·§5.2). 새 의존성 없이 ACTION_VIEW.
 * 사용자가 브라우저를 그냥 닫고 돌아오면 복귀가 오지 않으므로 3분 뒤 취소(null)로 끝낸다
 */
@Composable
actual fun rememberAppleSignInLauncher(): AppleSignInLauncher {
    val context = LocalContext.current
    return remember(context) {
        object : AppleSignInLauncher {
            override val isAvailable: Boolean = AppleAuthConfig.SERVICES_ID.isNotEmpty()

            override suspend fun signIn(): AppleExchangeCredential? = coroutineScope {
                val request = AppleAuthRequest.create("ANDROID")
                // 브라우저를 열기 전에 구독 — 빠른 복귀를 놓치지 않게
                val redirect = async(start = CoroutineStart.UNDISPATCHED) {
                    AppleAuthRedirects.params.first { it["nonce"] == request.nonce }
                }
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(request.authorizeUrl())))
                val params = withTimeoutOrNull(180_000) { redirect.await() }
                if (params == null) {
                    redirect.cancel()
                    null
                } else {
                    request.complete(params)
                }
            }
        }
    }
}
