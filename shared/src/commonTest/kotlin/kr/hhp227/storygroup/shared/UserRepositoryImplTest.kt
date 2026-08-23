package kr.hhp227.storygroup.shared

import kotlinx.coroutines.test.runTest
import kr.hhp227.storygroup.shared.data.network.dto.BlockedUserResponse
import kr.hhp227.storygroup.shared.data.network.dto.ProfileResponse
import kr.hhp227.storygroup.shared.data.network.dto.PublicProfileResponse
import kr.hhp227.storygroup.shared.data.repository.UserRepositoryImpl
import kr.hhp227.storygroup.shared.data.source.UserRemoteDataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserRepositoryImplTest {
    /** 전송 없는 가짜 소스 — 시임(인터페이스) 도입의 실효 증명 */
    private class FakeUserRemoteDataSource(
        private val profile: ProfileResponse? = null,
        private val error: Throwable? = null
    ) : UserRemoteDataSource {
        override suspend fun getMyProfile(): ProfileResponse = error?.let { throw it } ?: profile!!
        override suspend fun updateMyProfile(name: String, profileImg: String?, bio: String?, statusMessage: String?): ProfileResponse = throw UnsupportedOperationException()
        override suspend fun changePassword(currentPassword: String, newPassword: String) = throw UnsupportedOperationException()
        override suspend fun reportUser(userId: Long, reason: String?) = throw UnsupportedOperationException()
        override suspend fun blockUser(userId: Long) = throw UnsupportedOperationException()
        override suspend fun unblockUser(userId: Long) = throw UnsupportedOperationException()
        override suspend fun getBlockedUsers(): List<BlockedUserResponse> = throw UnsupportedOperationException()
        override suspend fun getPublicProfile(userId: Long): PublicProfileResponse = throw UnsupportedOperationException()
    }

    @Test
    fun getMyProfileMapsDtoToDomain() = runTest {
        val repository = UserRepositoryImpl(
            FakeUserRemoteDataSource(
                // ProfileResponse에 필수 필드가 더 있으면 named 인자 더미로 채운다 — 검증 대상은 매핑 통과뿐
                profile = ProfileResponse(id = 1L, name = "홍희표", email = "a@b.c", profileImg = null, bio = null, statusMessage = null, isAdmin = false)
            )
        )

        val result = repository.getMyProfile()

        assertTrue(result.isSuccess)
        assertEquals("홍희표", result.getOrThrow().name)
        assertEquals(1L, result.getOrThrow().id)
    }

    @Test
    fun getMyProfileWrapsErrorInFailure() = runTest {
        val repository = UserRepositoryImpl(FakeUserRemoteDataSource(error = IllegalStateException("boom")))

        val result = repository.getMyProfile()

        assertTrue(result.isFailure)
        assertEquals("boom", result.exceptionOrNull()?.message)
    }
}
