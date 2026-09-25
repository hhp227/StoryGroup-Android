package kr.hhp227.storygroup.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kr.hhp227.storygroup.shared.config.GoogleAuthConfig

/**
 * Credential Manager "Google로 로그인" 시트 — serverClientId=웹 클라이언트라 ID 토큰 aud가 웹으로 온다.
 * 계정 선택 UI에 Activity 컨텍스트가 필요해 LocalContext(=MainActivity)를 쓴다.
 * Android OAuth 클라이언트(패키지+SHA-1)가 콘솔에 없으면 getCredential이 예외 — 화면이 에러로 보여준다
 */
@Composable
actual fun rememberGoogleSignInLauncher(): GoogleSignInLauncher {
    val context = LocalContext.current
    return remember(context) {
        object : GoogleSignInLauncher {
            override val isAvailable: Boolean = GoogleAuthConfig.WEB_CLIENT_ID.isNotEmpty()

            override suspend fun signIn(): GoogleCredential? {
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(GetSignInWithGoogleOption.Builder(GoogleAuthConfig.WEB_CLIENT_ID).build())
                    .build()
                val result = try {
                    CredentialManager.create(context).getCredential(context, request)
                } catch (e: GetCredentialCancellationException) {
                    return null
                }
                val credential = result.credential
                if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    throw IllegalStateException("지원하지 않는 자격 증명입니다")
                }
                return GoogleCredential.IdToken(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            }
        }
    }
}
