package kr.hhp227.storygroup.shared.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kr.hhp227.storygroup.shared.data.network.dto.ProfileResponse
import kr.hhp227.storygroup.shared.domain.model.Profile
import kr.hhp227.storygroup.shared.domain.repository.UserRepository

class UserRepositoryImpl(private val client: HttpClient) : UserRepository {

    override suspend fun getMyProfile(): Result<Profile> =
        runCatching { client.get("/api/users/me").body<ProfileResponse>().toDomain() }
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
