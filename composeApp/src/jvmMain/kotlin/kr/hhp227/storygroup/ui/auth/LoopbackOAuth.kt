package kr.hhp227.storygroup.ui.auth

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** RFC 7636 PKCE(S256) */
object Pkce {
    private val random = SecureRandom()

    fun newVerifier(): String = randomUrlSafe(32)

    fun challenge(verifier: String): String =
        Base64.getUrlEncoder().withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    internal fun randomUrlSafe(bytes: Int): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(bytes).also(random::nextBytes))
}

/**
 * 설치형 앱 OAuth(RFC 8252) — 127.0.0.1 임의 포트에 1회용 서버를 띄우고 시스템 브라우저로 구글 동의 화면을 연다.
 * 콜백의 state를 검증해 code를 돌려주고 서버는 항상 닫는다. 코드 교환은 서버(/api/auth/google/code) 몫.
 */
class LoopbackOAuth(
    private val clientId: String,
    private val timeoutMillis: Long = 180_000,
    private val openBrowser: (String) -> Unit
) {
    /** 테스트 확인용 — 마지막으로 연 인가 URL */
    var lastAuthUrl: String? = null
        private set

    /** 사용자가 동의 화면에서 거부하면 null, state 불일치·기타 오류는 예외, 시간 초과는 TimeoutCancellationException */
    suspend fun authorize(): GoogleCredential.AuthCode? {
        val verifier = Pkce.newVerifier()
        val state = Pkce.randomUrlSafe(16)
        val result = CompletableDeferred<Map<String, String>>()
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/") { exchange ->
            val params = parseQuery(exchange.requestURI.rawQuery)
            val body = DONE_HTML.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
            // 파비콘 등 code/error 없는 요청은 무시하고 진짜 콜백만 완료시킨다
            if ("code" in params || "error" in params) result.complete(params)
        }
        server.start()
        try {
            val redirectUri = "http://127.0.0.1:${server.address.port}"
            val authUrl = buildAuthUrl(redirectUri, Pkce.challenge(verifier), state)
            lastAuthUrl = authUrl
            openBrowser(authUrl)
            val params = withTimeout(timeoutMillis) { result.await() }
            if (params["state"] != state) throw IllegalStateException("로그인 응답이 올바르지 않습니다")
            if (params["error"] == "access_denied") return null
            val code = params["code"] ?: throw IllegalStateException("구글 로그인에 실패했습니다: ${params["error"]}")
            return GoogleCredential.AuthCode(code = code, codeVerifier = verifier, redirectUri = redirectUri)
        } finally {
            server.stop(0)
        }
    }

    internal fun buildAuthUrl(redirectUri: String, challenge: String, state: String): String {
        val params = linkedMapOf(
            "client_id" to clientId,
            "redirect_uri" to redirectUri,
            "response_type" to "code",
            "scope" to "openid email profile",
            "code_challenge" to challenge,
            "code_challenge_method" to "S256",
            "state" to state,
            "prompt" to "select_account"
        )
        return AUTH_ENDPOINT + "?" + params.entries.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> =
        rawQuery.orEmpty().split("&").filter { it.contains('=') }
            .associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8") }

    private companion object {
        const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
        const val DONE_HTML = "<!doctype html><meta charset=utf-8><title>StoryGroup</title>" +
            "<p style=\"font-family:sans-serif;padding:2em\">로그인 처리가 끝났습니다. StoryGroup 앱으로 돌아가세요.</p>"
    }
}
