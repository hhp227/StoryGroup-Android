package kr.hhp227.storygroup.shared.data.source

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kr.hhp227.storygroup.shared.data.network.clearAuthTokenCache
import kr.hhp227.storygroup.shared.data.network.dto.LoginRequest
import kr.hhp227.storygroup.shared.data.network.dto.RefreshTokenRequest
import kr.hhp227.storygroup.shared.data.network.dto.RegisterRequest
import kr.hhp227.storygroup.shared.data.network.dto.TokenResponse
import kr.hhp227.storygroup.shared.data.network.dto.UserSummaryResponse

/** 인증 원격 소스 — 전송·DTO만 담당, 예외는 그대로 던진다(Result 래핑·토큰 저장은 리포지토리 몫) */
interface AuthRemoteDataSource {
    suspend fun register(name: String, email: String, password: String): UserSummaryResponse
    suspend fun login(email: String, password: String): TokenResponse
    suspend fun logout(refreshToken: String)

    /** Bearer 플러그인의 loadTokens 캐시 초기화 — HttpClient 내부 상태 전용, 로그인/로그아웃 후 반드시 호출 */
    fun clearAuthTokenCache()
}

class AuthRemoteDataSourceImpl(private val client: HttpClient) : AuthRemoteDataSource {
    override suspend fun register(name: String, email: String, password: String): UserSummaryResponse =
        client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(name = name, email = email, password = password))
        }.body()

    override suspend fun login(email: String, password: String): TokenResponse =
        client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email = email, password = password))
        }.body()

    override suspend fun logout(refreshToken: String) {
        client.post("/api/auth/logout") {
            contentType(ContentType.Application.Json)
            setBody(RefreshTokenRequest(refreshToken))
        }
    }

    override fun clearAuthTokenCache() {
        client.clearAuthTokenCache()
    }
}
