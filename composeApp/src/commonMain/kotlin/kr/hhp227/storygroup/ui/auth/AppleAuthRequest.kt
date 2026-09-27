package kr.hhp227.storygroup.ui.auth

import kr.hhp227.storygroup.shared.config.AppleAuthConfig
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

internal expect fun secureRandomBytes(size: Int): ByteArray
internal expect fun sha256Digest(input: ByteArray): ByteArray

/**
 * Android·Desktop 애플 인가 1회분 — state에 복귀 대상·verifier 해시를 싣고(서버 AppleCallbackState와 같은 형식),
 * 복귀 쿼리를 자격 증명으로 바꾼다. nonce로 자기 요청의 응답인지 확인한다
 */
@OptIn(ExperimentalEncodingApi::class)
class AppleAuthRequest private constructor(
    val verifier: String,
    val nonce: String,
    val state: String
) {
    fun authorizeUrl(): String {
        val params = linkedMapOf(
            "client_id" to AppleAuthConfig.SERVICES_ID,
            "redirect_uri" to AppleAuthConfig.CALLBACK_URL,
            "response_type" to "code id_token",
            "response_mode" to "form_post",
            "scope" to "name email",
            "state" to state
        )
        return AUTHORIZE_ENDPOINT + "?" + params.entries.joinToString("&") { (k, v) -> "$k=${v.percentEncode()}" }
    }

    fun complete(params: Map<String, String>): AppleExchangeCredential? {
        if (params["nonce"] != nonce) throw IllegalStateException("로그인 응답이 올바르지 않습니다")
        when (params["error"]) {
            null -> Unit
            "user_cancelled_authorize" -> return null
            "duplicate_email" -> throw IllegalStateException("이미 가입된 이메일입니다. 이메일로 로그인해주세요.")
            "not_configured" -> throw IllegalStateException("애플 로그인이 설정되지 않았습니다")
            else -> throw IllegalStateException("애플 로그인에 실패했습니다")
        }
        val code = params["code"] ?: throw IllegalStateException("애플 로그인에 실패했습니다")
        return AppleExchangeCredential(code, verifier)
    }

    companion object {
        private const val AUTHORIZE_ENDPOINT = "https://appleid.apple.com/auth/authorize"
        private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

        fun create(
            platform: String,
            port: Int? = null,
            randomBytes: (Int) -> ByteArray = ::secureRandomBytes,
            sha256: (ByteArray) -> ByteArray = ::sha256Digest
        ): AppleAuthRequest {
            val verifier = base64Url.encode(randomBytes(32))
            val nonce = base64Url.encode(randomBytes(16))
            // 값이 enum·정수·base64url뿐이라 이스케이프 없이 조립해도 안전
            val portField = port?.let { ""","port":$it""" }.orEmpty()
            val json = """{"p":"$platform"$portField,"n":"$nonce","vh":"${hashVerifier(verifier, sha256)}"}"""
            return AppleAuthRequest(verifier, nonce, base64Url.encode(json.encodeToByteArray()))
        }

        /** 서버 AppleLoginCodes.hashVerifier와 같은 계산 */
        fun hashVerifier(verifier: String, sha256: (ByteArray) -> ByteArray): String =
            base64Url.encode(sha256(verifier.encodeToByteArray()))

        // RFC 3986 unreserved 외 전부 %XX — 공백은 %20(애플 문서 예시와 같음)
        private fun String.percentEncode(): String = buildString {
            for (byte in this@percentEncode.encodeToByteArray()) {
                val c = byte.toInt().toChar()
                if (c.isLetterOrDigit() && byte >= 0 || c in "-._~") append(c)
                else append('%').append((byte.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0'))
            }
        }
    }
}
