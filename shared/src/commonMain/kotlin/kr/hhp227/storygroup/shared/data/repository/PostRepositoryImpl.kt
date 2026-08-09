package kr.hhp227.storygroup.shared.data.repository

import app.cash.paging.Pager
import app.cash.paging.PagingData
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kr.hhp227.storygroup.shared.data.network.dto.CommentResponse
import kr.hhp227.storygroup.shared.data.network.dto.CreateCommentRequest
import kr.hhp227.storygroup.shared.data.network.dto.CreatePostRequest
import kr.hhp227.storygroup.shared.data.network.dto.PostLikeResponse
import kr.hhp227.storygroup.shared.data.network.dto.PostResponse
import kr.hhp227.storygroup.shared.data.network.dto.UpdatePostRequest
import kr.hhp227.storygroup.shared.data.paging.PagePagingConfig
import kr.hhp227.storygroup.shared.data.paging.PagePagingSource
import kr.hhp227.storygroup.shared.domain.model.Comment
import kr.hhp227.storygroup.shared.domain.model.Group
import kr.hhp227.storygroup.shared.domain.model.Post
import kr.hhp227.storygroup.shared.domain.model.PostLike
import kr.hhp227.storygroup.shared.domain.repository.GroupRepository
import kr.hhp227.storygroup.shared.domain.repository.PostRepository

// 라운지 피드는 그룹 목록에서 라운지를 찾아야 해서 GroupRepository에 의존한다(웹 메인 피드 미러)
class PostRepositoryImpl(
    private val client: HttpClient,
    private val groupRepository: GroupRepository
) : PostRepository {
    // 구독자(홈·그룹 피드)가 살아 있는 동안만 의미 있는 일회성 신호라 replay는 두지 않는다
    private val _postUpdates =
        MutableSharedFlow<Post>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val postUpdates: Flow<Post> = _postUpdates.asSharedFlow()

    override suspend fun getPosts(groupId: Long, page: Int, size: Int): Result<List<Post>> =
        runCatching {
            client.get("/api/groups/$groupId/posts") {
                parameter("page", page)
                parameter("size", size)
            }.body<List<PostResponse>>().map { it.toDomain() }
        }

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

    override suspend fun createPost(groupId: Long, text: String, images: List<String>): Result<Post> =
        runCatching {
            client.post("/api/groups/$groupId/posts") {
                contentType(ContentType.Application.Json)
                setBody(CreatePostRequest(text = text, images = images.ifEmpty { null }))
            }.body<PostResponse>().toDomain()
        }

    override suspend fun createLoungePost(text: String, images: List<String>): Result<Post> =
        runCatching { createPost(resolveLoungeId(), text, images).getOrThrow() }

    override suspend fun getPost(groupId: Long, postId: Long): Result<Post> =
        runCatching { client.get("/api/groups/$groupId/posts/$postId").body<PostResponse>().toDomain() }

    override suspend fun updatePost(groupId: Long, postId: Long, text: String, images: List<String>): Result<Post> =
        runCatching {
            client.patch("/api/groups/$groupId/posts/$postId") {
                contentType(ContentType.Application.Json)
                // videos는 보내지 않는다(null=유지) — 앱 작성 폼에 동영상 첨부가 없어
                // 수정에서만 건드리면 웹에서 올린 동영상을 앱이 지워버리게 된다.
                setBody(UpdatePostRequest(text = text, images = images))
            }.body<PostResponse>().toDomain()
        }.onSuccess { post ->
            // 목록은 이 알림으로 그 항목만 갈아끼운다 — 재조회(refresh)는 첫 페이지부터 다시 읽어
            // 이미 쌓아둔 페이지와 스크롤 위치를 잃는다(수정은 목록 구조를 바꾸지 않는다)
            _postUpdates.tryEmit(post)
        }

    override suspend fun deletePost(groupId: Long, postId: Long): Result<Unit> =
        runCatching { client.delete("/api/groups/$groupId/posts/$postId") }.map { }

    override suspend fun getPostLikes(groupId: Long, postId: Long): Result<List<PostLike>> =
        runCatching {
            client.get("/api/groups/$groupId/posts/$postId/likes").body<List<PostLikeResponse>>().map { it.toDomain() }
        }

    override suspend fun setPostLiked(groupId: Long, postId: Long, liked: Boolean): Result<Unit> =
        runCatching {
            val path = "/api/groups/$groupId/posts/$postId/likes"

            if (liked) client.post(path) else client.delete(path)
        }.map { }

    override suspend fun getComments(groupId: Long, postId: Long): Result<List<Comment>> =
        runCatching {
            client.get("/api/groups/$groupId/posts/$postId/comments").body<List<CommentResponse>>().map { it.toDomain() }
        }

    override suspend fun createComment(
        groupId: Long,
        postId: Long,
        text: String,
        parentReplyId: Long?
    ): Result<Comment> =
        runCatching {
            client.post("/api/groups/$groupId/posts/$postId/comments") {
                contentType(ContentType.Application.Json)
                setBody(CreateCommentRequest(text = text, parentReplyId = parentReplyId))
            }.body<CommentResponse>().toDomain()
        }

    override suspend fun deleteComment(groupId: Long, postId: Long, commentId: Long): Result<Unit> =
        runCatching { client.delete("/api/groups/$groupId/posts/$postId/comments/$commentId") }.map { }

    private suspend fun resolveLoungeId(): Long =
        groupRepository.getMyGroups().getOrThrow().firstOrNull(Group::isLounge)?.id
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
    createdAt = createdAt
)
