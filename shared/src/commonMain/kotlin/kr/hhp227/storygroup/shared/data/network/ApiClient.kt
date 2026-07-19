package kr.hhp227.storygroup.shared.data.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.authProviders
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kr.hhp227.storygroup.shared.data.network.dto.RefreshTokenRequest
import kr.hhp227.storygroup.shared.data.network.dto.TokenResponse
import kr.hhp227.storygroup.shared.data.storage.TokenStorage
import kr.hhp227.storygroup.shared.domain.model.AuthTokens

object StoryGroupApi {
    const val DEFAULT_BASE_URL = "https://storygroup-k4cgcgz2ya-du.a.run.app"
}

/**
 * Bearer 토큰 자동 첨부 + 401 시 /api/auth/refresh로 재발급하는 공용 HttpClient.
 * 엔진은 각 플랫폼 아티팩트(okhttp/darwin)가 클래스패스에서 자동 선택된다.
 */
fun createApiClient(
    tokenStorage: TokenStorage,
    baseUrl: String = StoryGroupApi.DEFAULT_BASE_URL
): HttpClient = HttpClient {
    expectSuccess = true

    // Cloud Run 콜드 스타트(수십 초) 동안 기본 소켓 타임아웃으로 끊기지 않게 넉넉히 —
    // 실측: 유휴 후 첫 요청이 SocketTimeoutException으로 실패해 시작 직후 피드가 에러로 떴다
    install(HttpTimeout) {
        requestTimeoutMillis = 30_000
        connectTimeoutMillis = 30_000
        socketTimeoutMillis = 30_000
    }
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        })
    }
    install(Auth) {
        bearer {
            loadTokens {
                tokenStorage.load()?.let { BearerTokens(it.accessToken, it.refreshToken) }
            }
            refreshTokens {
                val refreshToken = oldTokens?.refreshToken
                    ?: tokenStorage.load()?.refreshToken
                    ?: return@refreshTokens null
                val response = runCatching {
                    client.post("$baseUrl/api/auth/refresh") {
                        markAsRefreshTokenRequest()
                        contentType(ContentType.Application.Json)
                        setBody(RefreshTokenRequest(refreshToken))
                    }.body<TokenResponse>()
                }.getOrNull()

                if (response == null) {
                    // 리프레시 토큰까지 만료 — 세션 종료로 간주
                    tokenStorage.clear()
                    null
                } else {
                    tokenStorage.save(AuthTokens(response.accessToken, response.refreshToken))
                    BearerTokens(response.accessToken, response.refreshToken)
                }
            }
            // 단일 호스트 전용 클라이언트라 첫 요청부터 토큰을 실어 보낸다
            sendWithoutRequest { true }
        }
    }
    defaultRequest {
        url(baseUrl)
    }
}

/**
 * Bearer 플러그인은 loadTokens 결과를 캐시하므로 로그인/로그아웃으로
 * 저장소가 바뀌면 반드시 호출해서 캐시를 비워야 한다.
 */
fun HttpClient.clearAuthTokenCache() {
    authProviders.filterIsInstance<BearerAuthProvider>().forEach { it.clearToken() }
}
