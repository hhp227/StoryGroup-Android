package kr.hhp227.storygroup.ui.auth

/**
 * Desktop 애플 로그인 — 루프백 서버를 먼저 띄우고 포트를 state에 실어 인가 페이지를 연다.
 * 애플은 루프백 Return URL을 허용하지 않아, 루프백 요청은 애플이 아니라 백엔드 복귀 페이지가 보낸다(설계 §2.4)
 */
class AppleLoopbackAuth(
    private val timeoutMillis: Long = 180_000,
    private val openBrowser: (String) -> Unit
) {
    /** 테스트 확인용 — 마지막으로 연 인가 URL */
    var lastAuthUrl: String? = null
        private set

    suspend fun authorize(): AppleExchangeCredential? {
        var request: AppleAuthRequest? = null
        val params = awaitLoopbackCallback(timeoutMillis) { port ->
            val created = AppleAuthRequest.create("DESKTOP", port)
            request = created
            lastAuthUrl = created.authorizeUrl()
            openBrowser(created.authorizeUrl())
        }
        return request!!.complete(params)
    }
}
