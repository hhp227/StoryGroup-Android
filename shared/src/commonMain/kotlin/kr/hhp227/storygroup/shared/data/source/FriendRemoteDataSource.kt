package kr.hhp227.storygroup.shared.data.source

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import kr.hhp227.storygroup.shared.data.network.dto.FriendResponse
import kr.hhp227.storygroup.shared.data.network.dto.SearchResponse

/** 친구 원격 소스 — 전송·DTO만 담당, 예외는 그대로 던진다(Result 래핑·도메인 매핑·에러 문구 변환은 리포지토리 몫) */
interface FriendRemoteDataSource {
    suspend fun getFriends(): List<FriendResponse>
    suspend fun addFriend(userId: Long)
    suspend fun removeFriend(userId: Long)
    suspend fun searchUsers(query: String, limit: Int): SearchResponse
}

class FriendRemoteDataSourceImpl(private val client: HttpClient) : FriendRemoteDataSource {
    override suspend fun getFriends(): List<FriendResponse> =
        client.get("/api/users/me/friends").body()

    override suspend fun addFriend(userId: Long) {
        client.post("/api/users/$userId/friend")
    }

    override suspend fun removeFriend(userId: Long) {
        client.delete("/api/users/$userId/friend")
    }

    override suspend fun searchUsers(query: String, limit: Int): SearchResponse =
        client.get("/api/search") {
            parameter("query", query)
            parameter("limit", limit)
        }.body()
}
