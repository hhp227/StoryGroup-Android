package kr.hhp227.storygroup.shared.data.repository

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kr.hhp227.storygroup.shared.data.network.dto.BlockedUserResponse
import kr.hhp227.storygroup.shared.data.network.dto.ProfileResponse
import kr.hhp227.storygroup.shared.data.network.dto.PublicProfileResponse
import kr.hhp227.storygroup.shared.data.source.UserRemoteDataSource
import kr.hhp227.storygroup.shared.domain.model.BlockedUser
import kr.hhp227.storygroup.shared.domain.model.Profile
import kr.hhp227.storygroup.shared.domain.model.PublicProfile
import kr.hhp227.storygroup.shared.domain.repository.UserRepository

class UserRepositoryImpl(private val userRemoteDataSource: UserRemoteDataSource) : UserRepository {
    // 구독자(홈·그룹 피드)가 살아 있는 동안만 의미 있는 일회성 신호라 replay는 두지 않는다
    private val _userBlocks =
        MutableSharedFlow<Long>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val userBlocks: Flow<Long> = _userBlocks.asSharedFlow()

    override suspend fun getMyProfile(): Result<Profile> =
        runCatching { userRemoteDataSource.getMyProfile().toDomain() }

    override suspend fun updateMyProfile(
        name: String,
        profileImg: String?,
        bio: String?,
        statusMessage: String?
    ): Result<Profile> =
        runCatching { userRemoteDataSource.updateMyProfile(name, profileImg, bio, statusMessage).toDomain() }

    override suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> =
        runCatching { userRemoteDataSource.changePassword(currentPassword, newPassword) }

    override suspend fun reportUser(userId: Long, reason: String?): Result<Unit> =
        runCatching { userRemoteDataSource.reportUser(userId, reason) }

    override suspend fun blockUser(userId: Long): Result<Unit> =
        runCatching { userRemoteDataSource.blockUser(userId) }.onSuccess {
            // 목록은 이 알림으로 그 작성자의 글만 걷어낸다 — 재조회(refresh)는 첫 페이지부터
            // 다시 읽어 이미 쌓아둔 페이지와 스크롤 위치를 잃는다
            _userBlocks.tryEmit(userId)
        }

    override suspend fun unblockUser(userId: Long): Result<Unit> =
        runCatching { userRemoteDataSource.unblockUser(userId) }

    override suspend fun getBlockedUsers(): Result<List<BlockedUser>> =
        runCatching { userRemoteDataSource.getBlockedUsers().map { it.toDomain() } }

    override suspend fun getPublicProfile(userId: Long): Result<PublicProfile> =
        runCatching { userRemoteDataSource.getPublicProfile(userId).toDomain() }
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

private fun BlockedUserResponse.toDomain() = BlockedUser(
    userId = userId,
    name = name,
    profileImg = profileImg,
    blockedAt = blockedAt
)

private fun PublicProfileResponse.toDomain() = PublicProfile(
    id = id,
    name = name,
    profileImg = profileImg,
    bio = bio,
    statusMessage = statusMessage,
    createdAt = createdAt
)
