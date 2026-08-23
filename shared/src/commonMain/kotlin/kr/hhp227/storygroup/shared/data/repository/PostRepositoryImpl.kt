package kr.hhp227.storygroup.shared.data.repository

import app.cash.paging.Pager
import app.cash.paging.PagingData
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kr.hhp227.storygroup.shared.data.network.dto.CommentResponse
import kr.hhp227.storygroup.shared.data.network.dto.ErrorResponse
import kr.hhp227.storygroup.shared.data.network.dto.PostLikeResponse
import kr.hhp227.storygroup.shared.data.network.dto.PostResponse
import kr.hhp227.storygroup.shared.data.paging.PagePagingConfig
import kr.hhp227.storygroup.shared.data.paging.PagePagingSource
import kr.hhp227.storygroup.shared.data.source.GroupRemoteDataSource
import kr.hhp227.storygroup.shared.data.source.PostRemoteDataSource
import kr.hhp227.storygroup.shared.domain.model.Comment
import kr.hhp227.storygroup.shared.domain.model.Post
import kr.hhp227.storygroup.shared.domain.model.PostLike
import kr.hhp227.storygroup.shared.domain.repository.PostRepository

// 라운지 피드는 그룹 목록에서 라운지를 찾아야 해서 GroupRemoteDataSource에 의존한다(같은 데이터 계층 하향 의존)
class PostRepositoryImpl(
    private val postRemoteDataSource: PostRemoteDataSource,
    private val groupRemoteDataSource: GroupRemoteDataSource
) : PostRepository {
    // 구독자(홈·그룹 피드)가 살아 있는 동안만 의미 있는 일회성 신호라 replay는 두지 않는다
    private val _postUpdates =
        MutableSharedFlow<Post>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val postUpdates: Flow<Post> = _postUpdates.asSharedFlow()

    private val _postDeletions =
        MutableSharedFlow<Long>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val postDeletions: Flow<Long> = _postDeletions.asSharedFlow()

    override suspend fun getPosts(groupId: Long, page: Int, size: Int): Result<List<Post>> =
        runCatching { postRemoteDataSource.getPosts(groupId, page, size).map { it.toDomain() } }

    override fun getGroupPostsPagingData(groupId: Long): Flow<PagingData<Post>> =
        Pager(PagePagingConfig) {
            PagePagingSource { page, size -> getPosts(groupId, page, size).getOrThrow() }
        }.flow

    override fun getLoungePostsPagingData(): Flow<PagingData<Post>> =
        Pager(PagePagingConfig) {
            // 라운지 id는 PagingSource 인스턴스 단위 캐시 — refresh(invalidate)마다 재해석된다
            var loungeId: Long? = null

            PagePagingSource { page, size ->
                val id = loungeId ?: resolveLoungeId().also { loungeId = it }

                getPosts(id, page, size).getOrThrow()
            }
        }.flow

    override suspend fun createPost(
        groupId: Long,
        text: String,
        images: List<String>,
        videos: List<String>
    ): Result<Post> =
        runCatching { postRemoteDataSource.createPost(groupId, text, images, videos).toDomain() }

    override suspend fun createLoungePost(text: String, images: List<String>, videos: List<String>): Result<Post> =
        runCatching { createPost(resolveLoungeId(), text, images, videos).getOrThrow() }

    override suspend fun getPost(groupId: Long, postId: Long): Result<Post> =
        runCatching { postRemoteDataSource.getPost(groupId, postId).toDomain() }

    override suspend fun updatePost(
        groupId: Long,
        postId: Long,
        text: String,
        images: List<String>,
        videos: List<String>
    ): Result<Post> =
        runCatching { postRemoteDataSource.updatePost(groupId, postId, text, images, videos).toDomain() }
            .onSuccess { post ->
                // 목록은 이 알림으로 그 항목만 갈아끼운다 — 재조회(refresh)는 첫 페이지부터 다시 읽어
                // 이미 쌓아둔 페이지와 스크롤 위치를 잃는다(수정은 목록 구조를 바꾸지 않는다)
                _postUpdates.tryEmit(post)
            }

    override suspend fun deletePost(groupId: Long, postId: Long): Result<Unit> =
        runCatching { postRemoteDataSource.deletePost(groupId, postId) }
            .onSuccess {
                // 목록은 이 알림으로 그 글만 걷어낸다 — 재조회(refresh)는 첫 페이지부터 다시 읽어
                // 이미 쌓아둔 페이지와 스크롤 위치를 잃는다
                _postDeletions.tryEmit(postId)
            }

    override suspend fun reportPost(groupId: Long, postId: Long, reason: String?): Result<Unit> =
        runCatching { postRemoteDataSource.reportPost(groupId, postId, reason) }

    override suspend fun getPostLikes(groupId: Long, postId: Long): Result<List<PostLike>> =
        runCatching { postRemoteDataSource.getPostLikes(groupId, postId).map { it.toDomain() } }

    override suspend fun setPostLiked(groupId: Long, postId: Long, liked: Boolean): Result<Unit> =
        runCatching {
            try {
                if (liked) postRemoteDataSource.likePost(groupId, postId) else postRemoteDataSource.unlikePost(groupId, postId)
            } catch (e: ClientRequestException) {
                // 좋아요 연타로 두 번째 POST가 첫 응답보다 먼저 나가면 서버가 409 CONFLICT/ALREADY_LIKED로
                // 거절한다(LikeService.kt:29, GlobalExceptionHandler.kt:24-26) — 좋아요는 이미 걸려 있으므로
                // 스퓨리어스 에러 다이얼로그 대신 성공으로 흡수한다(상태는 아래 getPost 재조회로 수렴).
                // DELETE(안 누른 글 취소)는 서버가 존재 여부 확인 없이 무조건 delete라 애초에 에러가 나지 않는다
                // (LikeService.kt:37-42) — 그 외 에러(401/403/500 등)는 그대로 전파한다.
                val code = runCatching { e.response.body<ErrorResponse>().code }.getOrNull()

                if (liked && e.response.status == HttpStatusCode.Conflict && code == "ALREADY_LIKED") Unit else throw e
            }
        }.onSuccess {
            // 목록 카드가 카운트를 그리므로 갱신된 단건을 다시 읽어 그 항목만 갈아끼우게 알린다
            // (수정 반영과 같은 규약 — 재조회라 남이 그 사이 누른 것까지 반영된다).
            // 재조회 실패는 무시 — 토글 자체는 성공했고 다음 갱신 기회에 맞춰진다.
            getPost(groupId, postId).onSuccess { post -> _postUpdates.tryEmit(post) }
        }

    override suspend fun getComments(groupId: Long, postId: Long): Result<List<Comment>> =
        runCatching { postRemoteDataSource.getComments(groupId, postId).map { it.toDomain() } }

    override suspend fun createComment(
        groupId: Long,
        postId: Long,
        text: String,
        parentReplyId: Long?
    ): Result<Comment> =
        runCatching { postRemoteDataSource.createComment(groupId, postId, text, parentReplyId).toDomain() }
            .onSuccess {
                // 목록 카드가 댓글 수(replyCount)를 그리므로 갱신된 단건을 다시 읽어 그 항목만 갈아끼우게 알린다
                // (setPostLiked와 같은 규약). 재조회 실패는 무시 — 댓글 작성 자체는 성공했다.
                getPost(groupId, postId).onSuccess { post -> _postUpdates.tryEmit(post) }
            }

    override suspend fun deleteComment(groupId: Long, postId: Long, commentId: Long): Result<Unit> =
        runCatching { postRemoteDataSource.deleteComment(groupId, postId, commentId) }
            .onSuccess {
                // 목록 카드가 댓글 수(replyCount)를 그리므로 갱신된 단건을 다시 읽어 그 항목만 갈아끼우게 알린다
                // (setPostLiked와 같은 규약). 재조회 실패는 무시 — 댓글 삭제 자체는 성공했다.
                getPost(groupId, postId).onSuccess { post -> _postUpdates.tryEmit(post) }
            }

    private suspend fun resolveLoungeId(): Long =
        groupRemoteDataSource.getMyGroups().firstOrNull { it.isLounge }?.id
            ?: error("라운지를 찾을 수 없습니다.")
}

private fun CommentResponse.toDomain() = Comment(
    id = id,
    postId = postId,
    userId = userId,
    authorName = authorName,
    authorProfileImg = authorProfileImg,
    parentReplyId = parentReplyId,
    text = text,
    createdAt = createdAt
)

private fun PostLikeResponse.toDomain() = PostLike(
    userId = userId,
    authorName = authorName,
    authorProfileImg = authorProfileImg,
    createdAt = createdAt
)

private fun PostResponse.toDomain() = Post(
    id = id,
    groupId = groupId,
    userId = userId,
    authorName = authorName,
    authorProfileImg = authorProfileImg,
    text = text,
    imageUrls = images.map { it.image },
    videoUrls = videos.map { it.video },
    isNotice = isNotice,
    createdAt = createdAt,
    likeCount = likeCount,
    replyCount = replyCount,
    likedByMe = likedByMe
)
