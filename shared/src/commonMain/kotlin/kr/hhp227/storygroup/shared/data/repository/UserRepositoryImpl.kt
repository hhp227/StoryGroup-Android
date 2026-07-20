package kr.hhp227.storygroup.shared.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kr.hhp227.storygroup.shared.data.network.dto.ChangePasswordRequest
import kr.hhp227.storygroup.shared.data.network.dto.ProfileResponse
import kr.hhp227.storygroup.shared.data.network.dto.UpdateProfileRequest
import kr.hhp227.storygroup.shared.domain.model.Profile
import kr.hhp227.storygroup.shared.domain.repository.UserRepository

class UserRepositoryImpl(private val client: HttpClient) : UserRepository {

    override suspend fun getMyProfile(): Result<Profile> =
        runCatching { client.get("/api/users/me").body<ProfileResponse>().toDomain() }

    override suspend fun updateMyProfile(
        name: String,
        profileImg: String?,
        bio: String?,
        statusMessage: String?
    ): Result<Profile> = runCatching {
        client.patch("/api/users/me") {
            contentType(ContentType.Application.Json)
            setBody(UpdateProfileRequest(name, profileImg, bio, statusMessage))
        }.body<ProfileResponse>().toDomain()
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> =
        runCatching {
            client.patch("/api/users/me/password") {
                contentType(ContentType.Application.Json)
                setBody(ChangePasswordRequest(currentPassword, newPassword))
            }
            Unit
        }
}

private fun ProfileResponse.toDomain() = Profile(
    id = id,
    name = name,
    email = email,
    profileImg = profileImg,
    bio = bio,
    statusMessage = statusMessage,
    isAdmin = isAdmin
)
