package kr.hhp227.storygroup.shared.data.source

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kr.hhp227.storygroup.shared.data.network.dto.BlockedUserResponse
import kr.hhp227.storygroup.shared.data.network.dto.ChangePasswordRequest
import kr.hhp227.storygroup.shared.data.network.dto.ProfileResponse
import kr.hhp227.storygroup.shared.data.network.dto.PublicProfileResponse
import kr.hhp227.storygroup.shared.data.network.dto.ReportUserRequest
import kr.hhp227.storygroup.shared.data.network.dto.UpdateProfileRequest

/** 사용자 원격 소스 — 전송·DTO만 담당, 예외는 그대로 던진다(Result 래핑·도메인 매핑은 리포지토리 몫) */
interface UserRemoteDataSource {
    suspend fun getMyProfile(): ProfileResponse
    suspend fun updateMyProfile(name: String, profileImg: String?, bio: String?, statusMessage: String?): ProfileResponse
    suspend fun changePassword(currentPassword: String, newPassword: String)
    suspend fun reportUser(userId: Long, reason: String?)
    suspend fun blockUser(userId: Long)
    suspend fun unblockUser(userId: Long)
    suspend fun getBlockedUsers(): List<BlockedUserResponse>
    suspend fun getPublicProfile(userId: Long): PublicProfileResponse
}

class UserRemoteDataSourceImpl(private val client: HttpClient) : UserRemoteDataSource {
    override suspend fun getMyProfile(): ProfileResponse =
        client.get("/api/users/me").body()

    override suspend fun updateMyProfile(name: String, profileImg: String?, bio: String?, statusMessage: String?): ProfileResponse =
        client.patch("/api/users/me") {
            contentType(ContentType.Application.Json)
            setBody(UpdateProfileRequest(name, profileImg, bio, statusMessage))
        }.body()

    override suspend fun changePassword(currentPassword: String, newPassword: String) {
        client.patch("/api/users/me/password") {
            contentType(ContentType.Application.Json)
            setBody(ChangePasswordRequest(currentPassword, newPassword))
        }
    }

    override suspend fun reportUser(userId: Long, reason: String?) {
        client.post("/api/users/$userId/report") {
            contentType(ContentType.Application.Json)
            setBody(ReportUserRequest(reason))
        }
    }

    override suspend fun blockUser(userId: Long) {
        client.post("/api/users/$userId/block")
    }

    override suspend fun unblockUser(userId: Long) {
        client.delete("/api/users/$userId/block")
    }

    override suspend fun getBlockedUsers(): List<BlockedUserResponse> =
        client.get("/api/users/me/blocks").body()

    override suspend fun getPublicProfile(userId: Long): PublicProfileResponse =
        client.get("/api/users/$userId").body()
}
