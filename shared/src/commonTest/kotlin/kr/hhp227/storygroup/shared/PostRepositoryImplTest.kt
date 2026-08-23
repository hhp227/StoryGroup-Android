package kr.hhp227.storygroup.shared

import kotlinx.coroutines.test.runTest
import kr.hhp227.storygroup.shared.data.network.dto.GroupResponse
import kr.hhp227.storygroup.shared.data.network.dto.PostResponse
import kr.hhp227.storygroup.shared.data.repository.PostRepositoryImpl
import kr.hhp227.storygroup.shared.data.source.GroupRemoteDataSource
import kr.hhp227.storygroup.shared.data.source.PostRemoteDataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PostRepositoryImplTest {
    /** 라운지 판별용 최소 그룹 DTO — GroupDtos.kt 계약(GroupResponse.isLounge) 그대로 */
    private fun group(id: Long, isLounge: Boolean) = GroupResponse(
        id = id, name = "g$id", joinType = "AUTO_APPROVE", myRole = "MEMBER",
        createdAt = "2026-01-01T00:00:00", isLounge = isLounge
    )

    /** 라운지 판별에만 쓰는 가짜 그룹 소스 — getMyGroups()만 응답하고, 그 외는 테스트 미사용 */
    private class FakeGroupRemoteDataSource(private val groups: List<GroupResponse>) : GroupRemoteDataSource {
        override suspend fun getMyGroups(): List<GroupResponse> = groups
        override suspend fun getMyGroups(page: Int, size: Int) = throw UnsupportedOperationException()
        override suspend fun getGroup(groupId: Long) = throw UnsupportedOperationException()
        override suspend fun getMembers(groupId: Long) = throw UnsupportedOperationException()
        override suspend fun getGroupPhotos(groupId: Long, page: Int, size: Int) = throw UnsupportedOperationException()
        override suspend fun createGroup(name: String, description: String?, image: String?, joinType: String) = throw UnsupportedOperationException()
        override suspend fun getDiscoverGroups(query: String, sort: String, page: Int, size: Int) = throw UnsupportedOperationException()
        override suspend fun joinGroup(groupId: Long) = throw UnsupportedOperationException()
        override suspend fun cancelJoinRequest(groupId: Long) = throw UnsupportedOperationException()
        override suspend fun getMyJoinRequestedGroups() = throw UnsupportedOperationException()
        override suspend fun getJoinRequests(groupId: Long) = throw UnsupportedOperationException()
        override suspend fun approveJoinRequest(groupId: Long, userId: Long) = throw UnsupportedOperationException()
        override suspend fun rejectJoinRequest(groupId: Long, userId: Long) = throw UnsupportedOperationException()
        override suspend fun createInvite(groupId: Long, maxUses: Int?, expiresInDays: Int?) = throw UnsupportedOperationException()
        override suspend fun joinByCode(code: String) = throw UnsupportedOperationException()
        override suspend fun updateGroup(groupId: Long, name: String, description: String?, image: String?, joinType: String?) = throw UnsupportedOperationException()
        override suspend fun deleteGroup(groupId: Long) = throw UnsupportedOperationException()
        override suspend fun leaveGroup(groupId: Long) = throw UnsupportedOperationException()
        override suspend fun getGroupReports(groupId: Long, status: String?) = throw UnsupportedOperationException()
        override suspend fun processGroupReport(groupId: Long, reportId: Long, status: String) = throw UnsupportedOperationException()
    }

    /** 전송 없는 가짜 Post 소스 — createPost 호출의 groupId만 기록, 그 외 메소드는 테스트 미사용 */
    private class FakePostRemoteDataSource : PostRemoteDataSource {
        var recordedGroupId: Long? = null

        override suspend fun getPosts(groupId: Long, page: Int, size: Int) = throw UnsupportedOperationException()

        override suspend fun createPost(groupId: Long, text: String, images: List<String>, videos: List<String>): PostResponse {
            recordedGroupId = groupId
            // 검증 대상은 groupId 라우팅뿐이라 그 외 필드는 named 인자 더미로 채운다
            return PostResponse(
                id = 1L,
                groupId = groupId,
                userId = 1L,
                authorName = "홍희표",
                text = text,
                createdAt = "2026-01-01T00:00:00"
            )
        }

        override suspend fun getPost(groupId: Long, postId: Long) = throw UnsupportedOperationException()
        override suspend fun updatePost(groupId: Long, postId: Long, text: String, images: List<String>, videos: List<String>) = throw UnsupportedOperationException()
        override suspend fun deletePost(groupId: Long, postId: Long) = throw UnsupportedOperationException()
        override suspend fun reportPost(groupId: Long, postId: Long, reason: String?) = throw UnsupportedOperationException()
        override suspend fun getPostLikes(groupId: Long, postId: Long) = throw UnsupportedOperationException()
        override suspend fun likePost(groupId: Long, postId: Long) = throw UnsupportedOperationException()
        override suspend fun unlikePost(groupId: Long, postId: Long) = throw UnsupportedOperationException()
        override suspend fun getComments(groupId: Long, postId: Long) = throw UnsupportedOperationException()
        override suspend fun createComment(groupId: Long, postId: Long, text: String, parentReplyId: Long?) = throw UnsupportedOperationException()
        override suspend fun deleteComment(groupId: Long, postId: Long, commentId: Long) = throw UnsupportedOperationException()
    }

    @Test
    fun createLoungePostRoutesToLoungeGroupId() = runTest {
        // fake GroupRemoteDataSource: [일반(1), 라운지(7)] 반환 → 라운지 id 7이 선택되어야 한다
        val postSource = FakePostRemoteDataSource()
        val repository = PostRepositoryImpl(
            postSource,
            FakeGroupRemoteDataSource(listOf(group(1L, isLounge = false), group(7L, isLounge = true)))
        )

        val result = repository.createLoungePost(text = "hello", images = emptyList(), videos = emptyList())

        // fake PostRemoteDataSource: 기록된 groupId == 7L 검증
        assertTrue(result.isSuccess)
        assertEquals(7L, postSource.recordedGroupId)
    }

    @Test
    fun createLoungePostFailsWhenNoLounge() = runTest {
        // fake GroupRemoteDataSource: 라운지 없는 목록 → Result.isFailure, 메시지 "라운지를 찾을 수 없습니다."
        val repository = PostRepositoryImpl(
            FakePostRemoteDataSource(),
            FakeGroupRemoteDataSource(listOf(group(1L, isLounge = false)))
        )

        val result = repository.createLoungePost(text = "hello", images = emptyList(), videos = emptyList())

        assertTrue(result.isFailure)
        assertEquals("라운지를 찾을 수 없습니다.", result.exceptionOrNull()?.message)
    }
}
