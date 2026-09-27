package kr.hhp227.storygroup.shared

import kotlinx.coroutines.test.runTest
import kr.hhp227.storygroup.shared.data.network.dto.AppleLoginRequest
import kr.hhp227.storygroup.shared.data.network.dto.TokenResponse
import kr.hhp227.storygroup.shared.data.network.dto.UserSummaryResponse
import kr.hhp227.storygroup.shared.data.repository.AuthRepositoryImpl
import kr.hhp227.storygroup.shared.data.source.AuthRemoteDataSource
import kr.hhp227.storygroup.shared.data.storage.TokenStorage
import kr.hhp227.storygroup.shared.domain.model.AuthTokens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthRepositoryImplTest {
    private class FakeAuthRemoteDataSource : AuthRemoteDataSource {
        var cacheCleared = 0
        var lastCode: Triple<String, String, String>? = null
        override suspend fun register(name: String, email: String, password: String): UserSummaryResponse = throw UnsupportedOperationException()
        override suspend fun login(email: String, password: String): TokenResponse = throw UnsupportedOperationException()
        override suspend fun loginWithGoogle(idToken: String) = TokenResponse(accessToken = "a-$idToken", refreshToken = "r", expiresIn = 1800)
        override suspend fun loginWithGoogleCode(code: String, codeVerifier: String, redirectUri: String): TokenResponse {
            lastCode = Triple(code, codeVerifier, redirectUri)
            return TokenResponse(accessToken = "a-code", refreshToken = "r", expiresIn = 1800)
        }
        var lastApple: AppleLoginRequest? = null
        var lastExchange: Pair<String, String>? = null
        override suspend fun loginWithApple(request: AppleLoginRequest): TokenResponse {
            lastApple = request
            return TokenResponse(accessToken = "a-apple", refreshToken = "r", expiresIn = 1800)
        }
        override suspend fun exchangeAppleCode(code: String, verifier: String): TokenResponse {
            lastExchange = code to verifier
            return TokenResponse(accessToken = "a-exchange", refreshToken = "r", expiresIn = 1800)
        }
        override suspend fun logout(refreshToken: String) = Unit
        override fun clearAuthTokenCache() { cacheCleared++ }
    }

    private class FakeTokenStorage : TokenStorage {
        var saved: AuthTokens? = null
        override fun load() = saved
        override fun save(tokens: AuthTokens) { saved = tokens }
        override fun clear() { saved = null }
    }

    @Test
    fun loginWithGoogleSavesTokensAndClearsCache() = runTest {
        val remote = FakeAuthRemoteDataSource()
        val storage = FakeTokenStorage()

        val result = AuthRepositoryImpl(remote, storage).loginWithGoogle("tok")

        assertTrue(result.isSuccess)
        assertEquals(AuthTokens("a-tok", "r"), storage.saved)
        assertEquals(1, remote.cacheCleared)
    }

    @Test
    fun loginWithGoogleCodePassesPkceTriple() = runTest {
        val remote = FakeAuthRemoteDataSource()
        val storage = FakeTokenStorage()

        AuthRepositoryImpl(remote, storage).loginWithGoogleCode("c", "v", "http://127.0.0.1:1234")

        assertEquals(Triple("c", "v", "http://127.0.0.1:1234"), remote.lastCode)
        assertEquals("a-code", storage.saved?.accessToken)
    }

    @Test
    fun loginWithAppleSendsNameOnceAndSavesTokens() = runTest {
        val remote = FakeAuthRemoteDataSource()
        val storage = FakeTokenStorage()

        AuthRepositoryImpl(remote, storage).loginWithApple("idt", "code", "IOS", "길동", "홍")

        assertEquals(AppleLoginRequest("idt", "code", "IOS", "길동", "홍"), remote.lastApple)
        assertEquals("a-apple", storage.saved?.accessToken)
        assertEquals(1, remote.cacheCleared)
    }

    @Test
    fun exchangeAppleCodeSavesTokens() = runTest {
        val remote = FakeAuthRemoteDataSource()
        val storage = FakeTokenStorage()

        AuthRepositoryImpl(remote, storage).exchangeAppleCode("c", "v")

        assertEquals("c" to "v", remote.lastExchange)
        assertEquals("a-exchange", storage.saved?.accessToken)
    }
}
