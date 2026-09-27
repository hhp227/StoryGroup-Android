package kr.hhp227.storygroup.ui.auth

import androidx.compose.runtime.Composable

/** 백엔드 콜백이 준 60초 코드 + 이 앱이 만든 verifier — /api/auth/apple/exchange로 토큰 교환(설계 2026-09-27 §5.2) */
data class AppleExchangeCredential(val code: String, val verifier: String)

interface AppleSignInLauncher {
    /** Services ID 미설정 등으로 쓸 수 없으면 false — 화면이 버튼을 숨긴다 */
    val isAvailable: Boolean

    /** 사용자 취소·시간 초과는 null(에러 표시 없음), 그 밖의 실패는 예외 */
    suspend fun signIn(): AppleExchangeCredential?
}

@Composable
expect fun rememberAppleSignInLauncher(): AppleSignInLauncher
