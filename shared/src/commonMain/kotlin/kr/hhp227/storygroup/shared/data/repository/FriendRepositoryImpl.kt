package kr.hhp227.storygroup.shared.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import kr.hhp227.storygroup.shared.data.network.dto.ErrorResponse
import kr.hhp227.storygroup.shared.data.network.dto.FriendResponse
import kr.hhp227.storygroup.shared.data.network.dto.SearchResponse
import kr.hhp227.storygroup.shared.data.network.dto.SearchUserResponse
import kr.hhp227.storygroup.shared.domain.model.Friend
import kr.hhp227.storygroup.shared.domain.model.UserSearchResult
import kr.hhp227.storygroup.shared.domain.repository.FriendRepository

class FriendRepositoryImpl(private val client: HttpClient) : FriendRepository {
    override suspend fun getFriends(): Result<List<Friend>> =
        runCatching {
            client.get("/api/users/me/friends").body<List<FriendResponse>>().map { it.toDomain() }
        }

    override suspend fun addFriend(userId: Long): Result<Unit> =
        runCatching {
            try {
                client.post("/api/users/$userId/friend")
                Unit
            } catch (e: ClientRequestException) {
                // 이미 등록(409 ALREADY_FRIEND)이 일상 실패 경로 — Ktor 예외 원문 대신
                // 서버 에러 본문의 사용자 문구를 그대로 보여준다(joinByCode 선례)
                val message = runCatching { e.response.body<ErrorResponse>().message }.getOrNull()
                throw IllegalStateException(message ?: "친구 등록에 실패했습니다.", e)
            }
        }

    override suspend fun removeFriend(userId: Long): Result<Unit> =
        runCatching {
            client.delete("/api/users/$userId/friend")
            Unit
        }

    override suspend fun searchUsers(query: String, limit: Int): Result<List<UserSearchResult>> =
        runCatching {
            client.get("/api/search") {
                parameter("query", query)
                parameter("limit", limit)
            }.body<SearchResponse>().users.map { it.toDomain() }
        }
}

private fun FriendResponse.toDomain() = Friend(
    userId = userId,
    name = name,
    profileImg = profileImg,
    statusMessage = statusMessage,
    friendedAt = friendedAt,
    online = online
)

private fun SearchUserResponse.toDomain() = UserSearchResult(
    id = id,
    name = name,
    profileImg = profileImg,
    statusMessage = statusMessage
)
