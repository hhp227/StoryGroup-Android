package kr.hhp227.storygroup.ui.auth

import kotlinx.coroutines.runBlocking
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLDecoder
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoopbackOAuthTest {
    @Test
    fun pkceChallengeMatchesRfc7636Example() {
        // RFC 7636 Appendix B
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
        assertTrue(Pkce.newVerifier().length >= 43)
    }

    private fun query(url: String): Map<String, String> =
        URI(url).rawQuery.split("&").associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8") }

    // 브라우저 대신 콜백 URL을 직접 두드린다 — authorize()가 서버를 띄운 뒤라 약간 늦춘다
    private fun hit(url: String) = thread {
        Thread.sleep(200)
        (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            responseCode
            disconnect()
        }
    }

    @Test
    fun returnsCodeWhenStateMatches() = runBlocking {
        val oauth = LoopbackOAuth("cid", openBrowser = { authUrl ->
            val q = query(authUrl)
            hit("${q.getValue("redirect_uri")}/?code=abc&state=${q.getValue("state")}")
        })
        val result = oauth.authorize()!!
        assertEquals("abc", result.code)
        assertTrue(result.redirectUri.startsWith("http://127.0.0.1:"))
        assertEquals(Pkce.challenge(result.codeVerifier), query(oauth.lastAuthUrl!!)["code_challenge"])
    }

    @Test
    fun accessDeniedIsCancel() = runBlocking {
        val oauth = LoopbackOAuth("cid", openBrowser = { authUrl ->
            val q = query(authUrl)
            hit("${q.getValue("redirect_uri")}/?error=access_denied&state=${q.getValue("state")}")
        })
        assertNull(oauth.authorize())
    }

    @Test
    fun stateMismatchFails() {
        val oauth = LoopbackOAuth("cid", openBrowser = { authUrl ->
            hit("${query(authUrl).getValue("redirect_uri")}/?code=abc&state=forged")
        })
        assertFailsWith<IllegalStateException> { runBlocking { oauth.authorize() } }
    }
}
