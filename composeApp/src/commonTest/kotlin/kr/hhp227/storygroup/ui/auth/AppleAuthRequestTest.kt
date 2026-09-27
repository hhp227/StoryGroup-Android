package kr.hhp227.storygroup.ui.auth

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalEncodingApi::class)
class AppleAuthRequestTest {
    // 결정적 난수·해시 — 서버와의 형식 계약(state JSON 키·base64url 무패딩)만 검사
    private val fixedRandom: (Int) -> ByteArray = { size -> ByteArray(size) { 1 } }
    private val fakeSha: (ByteArray) -> ByteArray = { byteArrayOf(9, 9, 9) }

    private fun decodeState(state: String): String =
        Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(state).decodeToString()

    @Test
    fun androidStateCarriesPlatformNonceAndVerifierHash() {
        val request = AppleAuthRequest.create("ANDROID", randomBytes = fixedRandom, sha256 = fakeSha)
        assertEquals("""{"p":"ANDROID","n":"${request.nonce}","vh":"CQkJ"}""", decodeState(request.state))
        assertTrue(request.verifier.length >= 43)
    }

    @Test
    fun desktopStateCarriesPort() {
        val request = AppleAuthRequest.create("DESKTOP", port = 51234, randomBytes = fixedRandom, sha256 = fakeSha)
        assertTrue(decodeState(request.state).contains(""""port":51234"""))
    }

    @Test
    fun authorizeUrlUsesFormPostAndCallback() {
        val url = AppleAuthRequest.create("ANDROID", randomBytes = fixedRandom, sha256 = fakeSha).authorizeUrl()
        assertTrue(url.startsWith("https://appleid.apple.com/auth/authorize?"))
        assertTrue(url.contains("response_type=code%20id_token"))
        assertTrue(url.contains("response_mode=form_post"))
        assertTrue(url.contains("scope=name%20email"))
        assertTrue(url.contains("redirect_uri=https%3A%2F%2F"))
    }

    @Test
    fun completeMapsCodeCancelAndErrors() {
        val request = AppleAuthRequest.create("ANDROID", randomBytes = fixedRandom, sha256 = fakeSha)
        val n = request.nonce
        assertEquals(AppleExchangeCredential("c1", request.verifier), request.complete(mapOf("code" to "c1", "nonce" to n)))
        assertNull(request.complete(mapOf("error" to "user_cancelled_authorize", "nonce" to n)))
        assertFailsWith<IllegalStateException> { request.complete(mapOf("code" to "c1", "nonce" to "forged")) }
        val dup = assertFailsWith<IllegalStateException> { request.complete(mapOf("error" to "duplicate_email", "nonce" to n)) }
        assertEquals("이미 가입된 이메일입니다. 이메일로 로그인해주세요.", dup.message)
        assertFailsWith<IllegalStateException> { request.complete(mapOf("error" to "invalid_token", "nonce" to n)) }
    }

    @Test
    fun realShaMatchesServerHash() {
        // 서버 AppleLoginCodes.hashVerifier와 같은 RFC 7636 예시
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            AppleAuthRequest.hashVerifier("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk", ::sha256Digest)
        )
    }
}
