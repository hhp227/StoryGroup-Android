package kr.hhp227.storygroup.shared.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlin.io.encoding.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kr.hhp227.storygroup.shared.data.network.clearAuthTokenCache
import kr.hhp227.storygroup.shared.data.network.dto.LoginRequest
import kr.hhp227.storygroup.shared.data.network.dto.RefreshTokenRequest
import kr.hhp227.storygroup.shared.data.network.dto.RegisterRequest
import kr.hhp227.storygroup.shared.data.network.dto.TokenResponse
import kr.hhp227.storygroup.shared.data.network.dto.UserSummaryResponse
import kr.hhp227.storygroup.shared.data.storage.TokenStorage
import kr.hhp227.storygroup.shared.domain.model.AuthTokens
import kr.hhp227.storygroup.shared.domain.model.User
import kr.hhp227.storygroup.shared.domain.repository.AuthRepository

class AuthRepositoryImpl(
    private val client: HttpClient,
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
            client.post("/api/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequest(name = name, email = email, password = password))
            }.body<UserSummaryResponse>().toDomain()
        }

    override suspend fun login(email: String, password: String): Result<AuthTokens> =
        runCatching {
            val response = client.post("/api/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(email = email, password = password))
            }.body<TokenResponse>()

            AuthTokens(response.accessToken, response.refreshToken).also {
                tokenStorage.save(it)
                client.clearAuthTokenCache()
            }
        }

    override suspend fun logout(): Result<Unit> {
        val refreshToken = tokenStorage.load()?.refreshToken
        val result = runCatching {
            if (refreshToken != null) {
                client.post("/api/auth/logout") {
                    contentType(ContentType.Application.Json)
                    setBody(RefreshTokenRequest(refreshToken))
                }
            }
            Unit
        }
        // 서버 호출 성패와 무관하게 로컬 세션은 정리한다
        tokenStorage.clear()
        client.clearAuthTokenCache()
        return result
    }
}

private fun UserSummaryResponse.toDomain() = User(
    id = id,
    name = name,
    email = email,
    profileImg = profileImg
)
