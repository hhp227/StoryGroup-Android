package kr.hhp227.storygroup.shared.data.source

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kr.hhp227.storygroup.shared.data.network.dto.RegisterPushTokenRequest

/** 푸시 토큰 원격 소스 — 전송만 담당, 예외는 그대로 던진다 */
interface PushTokenRemoteDataSource {
    suspend fun register(token: String, platform: String)
    suspend fun unregister(token: String)
}

class PushTokenRemoteDataSourceImpl(private val client: HttpClient) : PushTokenRemoteDataSource {
    override suspend fun register(token: String, platform: String) {
        client.put("/api/push-tokens") {
            contentType(ContentType.Application.Json)
            setBody(RegisterPushTokenRequest(token, platform))
        }
    }

    override suspend fun unregister(token: String) {
        client.delete("/api/push-tokens") { parameter("token", token) }
    }
}
