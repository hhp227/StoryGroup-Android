package kr.hhp227.storygroup.shared.data.repository

import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import kotlin.io.encoding.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kr.hhp227.storygroup.shared.data.network.dto.ErrorResponse
import kr.hhp227.storygroup.shared.data.network.dto.TokenResponse
import kr.hhp227.storygroup.shared.data.network.dto.UserSummaryResponse
import kr.hhp227.storygroup.shared.data.source.AuthRemoteDataSource
import kr.hhp227.storygroup.shared.data.storage.TokenStorage
import kr.hhp227.storygroup.shared.domain.model.AuthTokens
import kr.hhp227.storygroup.shared.domain.model.User
import kr.hhp227.storygroup.shared.domain.repository.AuthRepository

class AuthRepositoryImpl(
    private val authRemoteDataSource: AuthRemoteDataSource,
    private val tokenStorage: TokenStorage
) : AuthRepository {

    override fun isLoggedIn(): Boolean = tokenStorage.load() != null

    override fun currentUserId(): Long? {
        val payload = tokenStorage.load()?.accessToken?.split('.')?.getOrNull(1) ?: return null
        return runCatching {
            val decoded = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
                .decode(payload)
                .decodeToString()
            Json.parseToJsonElement(decoded).jsonObject["sub"]?.jsonPrimitive?.content?.toLong()
        }.getOrNull()
    }

    override suspend fun register(name: String, email: String, password: String): Result<User> =
        runCatching {
            authRemoteDataSource.register(name, email, password).toDomain()
        }

    override suspend fun login(email: String, password: String): Result<AuthTokens> =
        runCatching { saveTokens(authRemoteDataSource.login(email, password)) }

    override suspend fun loginWithGoogle(idToken: String): Result<AuthTokens> =
        runCatching { saveTokens(serverMessageOnFailure { authRemoteDataSource.loginWithGoogle(idToken) }) }

    override suspend fun loginWithGoogleCode(code: String, codeVerifier: String, redirectUri: String): Result<AuthTokens> =
        runCatching {
            saveTokens(serverMessageOnFailure { authRemoteDataSource.loginWithGoogleCode(code, codeVerifier, redirectUri) })
        }

    private fun saveTokens(response: TokenResponse): AuthTokens =
        AuthTokens(response.accessToken, response.refreshToken).also {
            tokenStorage.save(it)
            authRemoteDataSource.clearAuthTokenCache()
        }

    // 401(구글 인증 실패)·409(미검증 이메일 충돌)는 일상 실패 경로 — Ktor 예외 원문 대신 서버 문구
    // (UserRepositoryImpl.deleteAccount 관용구)
    private suspend fun <T> serverMessageOnFailure(block: suspend () -> T): T =
        try {
            block()
        } catch (e: ClientRequestException) {
            val message = runCatching { e.response.body<ErrorResponse>().message }.getOrNull()
            throw IllegalStateException(message ?: "구글 로그인에 실패했습니다.", e)
        }

    override suspend fun logout(): Result<Unit> {
        val refreshToken = tokenStorage.load()?.refreshToken
        val result = runCatching {
            if (refreshToken != null) {
                authRemoteDataSource.logout(refreshToken)
            }
            Unit
        }
        // 서버 호출 성패와 무관하게 로컬 세션은 정리한다
        tokenStorage.clear()
        authRemoteDataSource.clearAuthTokenCache()
        return result
    }
}

private fun UserSummaryResponse.toDomain() = User(
    id = id,
    name = name,
    email = email,
    profileImg = profileImg
)
