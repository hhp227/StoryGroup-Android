package kr.hhp227.storygroup.ui.auth

import kotlinx.coroutines.runBlocking
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLDecoder
import java.util.Base64
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

// 브라우저·애플·백엔드 대신 "백엔드 복귀 페이지가 루프백을 여는" 마지막 단계만 흉내 낸다
class AppleLoopbackAuthTest {
    private fun stateJson(authUrl: String): String {
        val state = URI(authUrl).rawQuery.split("&").first { it.startsWith("state=") }.substringAfter('=')
        return String(Base64.getUrlDecoder().decode(URLDecoder.decode(state, "UTF-8")))
    }

    private fun field(json: String, name: String) = Regex(""""$name":"?([^",}]+)""").find(json)!!.groupValues[1]

    private fun hit(url: String) = thread {
        Thread.sleep(200)
        (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            responseCode
            disconnect()
        }
    }

    @Test
    fun returnsCodeWithVerifierWhenNonceMatches() = runBlocking {
        val auth = AppleLoopbackAuth(openBrowser = { url ->
            val json = stateJson(url)
            assertEquals("DESKTOP", field(json, "p"))
            hit("http://127.0.0.1:${field(json, "port")}/?code=c1&nonce=${field(json, "n")}")
        })
        val credential = auth.authorize()!!
        assertEquals("c1", credential.code)
        assertEquals(field(stateJson(auth.lastAuthUrl!!), "vh"), AppleAuthRequest.hashVerifier(credential.verifier, ::sha256Digest))
    }

    @Test
    fun userCancelIsNull() = runBlocking {
        val auth = AppleLoopbackAuth(openBrowser = { url ->
            val json = stateJson(url)
            hit("http://127.0.0.1:${field(json, "port")}/?error=user_cancelled_authorize&nonce=${field(json, "n")}")
        })
        assertNull(auth.authorize())
    }

    @Test
    fun forgedNonceFails() {
        val auth = AppleLoopbackAuth(openBrowser = { url ->
            hit("http://127.0.0.1:${field(stateJson(url), "port")}/?code=c1&nonce=forged")
        })
        assertFailsWith<IllegalStateException> { runBlocking { auth.authorize() } }
    }
}
