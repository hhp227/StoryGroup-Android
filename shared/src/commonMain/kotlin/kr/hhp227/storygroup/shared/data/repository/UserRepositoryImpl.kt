package kr.hhp227.storygroup.shared.data.repository

import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kr.hhp227.storygroup.shared.data.network.dto.BlockedUserResponse
import kr.hhp227.storygroup.shared.data.network.dto.ErrorResponse
import kr.hhp227.storygroup.shared.data.network.dto.ProfileResponse
import kr.hhp227.storygroup.shared.data.network.dto.PublicProfileResponse
import kr.hhp227.storygroup.shared.data.network.dto.PushPreferencesResponse
import kr.hhp227.storygroup.shared.data.source.UserRemoteDataSource
import kr.hhp227.storygroup.shared.domain.model.BlockedUser
import kr.hhp227.storygroup.shared.domain.model.Profile
import kr.hhp227.storygroup.shared.domain.model.PublicProfile
import kr.hhp227.storygroup.shared.domain.model.PushPreferences
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

    override suspend fun deleteAccount(password: String): Result<Unit> =
        runCatching {
            try {
                userRemoteDataSource.deleteAccount(password)
            } catch (e: ClientRequestException) {
                // 비밀번호 불일치(400)/미탈퇴 그룹 존재(409)가 일상 실패 경로 — Ktor 예외 원문 대신
                // 서버 에러 본문의 사용자 문구를 그대로 보여준다(joinByCode 선례)
                val message = runCatching { e.response.body<ErrorResponse>().message }.getOrNull()
                throw IllegalStateException(message ?: "계정 삭제에 실패했습니다.", e)
            }
        }

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

    override suspend fun getPushPreferences(): Result<PushPreferences> =
        runCatching { userRemoteDataSource.getPushPreferences().toDomain() }

    override suspend fun updatePushPreferences(chatEnabled: Boolean, activityEnabled: Boolean): Result<Unit> =
        runCatching {
            try {
                userRemoteDataSource.updatePushPreferences(chatEnabled, activityEnabled)
            } catch (e: ClientRequestException) {
                // deleteAccount와 같은 관용구 — Ktor 예외 원문 대신 서버 에러 본문의 사용자 문구를 보여준다
                val message = runCatching { e.response.body<ErrorResponse>().message }.getOrNull()
                throw IllegalStateException(message ?: "알림 설정을 저장하지 못했습니다.", e)
            }
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

private fun PushPreferencesResponse.toDomain() = PushPreferences(
    chatEnabled = chatEnabled,
    activityEnabled = activityEnabled
)
