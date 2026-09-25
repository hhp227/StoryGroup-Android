package kr.hhp227.storygroup.ui.auth

import androidx.compose.runtime.Composable

/** 플랫폼이 얻어 온 구글 자격 증명 — 서버는 둘 다 같은 계정 결정 경로로 처리한다(설계 2026-09-25 §5.2) */
sealed interface GoogleCredential {
    /** Android Credential Manager가 준 ID 토큰(aud=웹 클라이언트) */
    data class IdToken(val idToken: String) : GoogleCredential

    /** Desktop 루프백 PKCE 인가 코드 — 교환은 서버가 한다(client_secret 비노출) */
    data class AuthCode(val code: String, val codeVerifier: String, val redirectUri: String) : GoogleCredential
}

interface GoogleSignInLauncher {
    /** 클라이언트 ID 미설정 등으로 쓸 수 없으면 false — 화면이 버튼을 숨긴다 */
    val isAvailable: Boolean

    /** 사용자 취소는 null(에러 표시 없음), 그 밖의 실패는 예외 */
    suspend fun signIn(): GoogleCredential?
}

@Composable
expect fun rememberGoogleSignInLauncher(): GoogleSignInLauncher
