package kr.hhp227.storygroup.shared.data.source

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
import kr.hhp227.storygroup.shared.data.network.dto.CommentResponse
import kr.hhp227.storygroup.shared.data.network.dto.CreateCommentRequest
import kr.hhp227.storygroup.shared.data.network.dto.CreatePostRequest
import kr.hhp227.storygroup.shared.data.network.dto.PostLikeResponse
import kr.hhp227.storygroup.shared.data.network.dto.PostResponse
import kr.hhp227.storygroup.shared.data.network.dto.ReportPostRequest
import kr.hhp227.storygroup.shared.data.network.dto.UpdatePostRequest

/** 게시글 원격 소스 — 전송·DTO만 담당, 예외는 그대로 던진다(Result 래핑·도메인 매핑·페이징 구성·상태코드 문구 변환은 리포지토리 몫) */
interface PostRemoteDataSource {
    suspend fun getPosts(groupId: Long, page: Int, size: Int): List<PostResponse>
    suspend fun createPost(groupId: Long, text: String, images: List<String>, videos: List<String>): PostResponse
    suspend fun getPost(groupId: Long, postId: Long): PostResponse
    suspend fun updatePost(groupId: Long, postId: Long, text: String, images: List<String>, videos: List<String>): PostResponse
    suspend fun deletePost(groupId: Long, postId: Long)
    suspend fun reportPost(groupId: Long, postId: Long, reason: String?)
    suspend fun getPostLikes(groupId: Long, postId: Long): List<PostLikeResponse>
    suspend fun likePost(groupId: Long, postId: Long)
    suspend fun unlikePost(groupId: Long, postId: Long)
    suspend fun getComments(groupId: Long, postId: Long): List<CommentResponse>
    suspend fun createComment(groupId: Long, postId: Long, text: String, parentReplyId: Long?): CommentResponse
    suspend fun deleteComment(groupId: Long, postId: Long, commentId: Long)
}

class PostRemoteDataSourceImpl(private val client: HttpClient) : PostRemoteDataSource {
    override suspend fun getPosts(groupId: Long, page: Int, size: Int): List<PostResponse> =
        client.get("/api/groups/$groupId/posts") {
            parameter("page", page)
            parameter("size", size)
        }.body()

    override suspend fun createPost(groupId: Long, text: String, images: List<String>, videos: List<String>): PostResponse =
        client.post("/api/groups/$groupId/posts") {
            contentType(ContentType.Application.Json)
            setBody(
                CreatePostRequest(
                    text = text,
                    images = images.ifEmpty { null },
                    videos = videos.ifEmpty { null }
                )
            )
        }.body()

    override suspend fun getPost(groupId: Long, postId: Long): PostResponse =
        client.get("/api/groups/$groupId/posts/$postId").body()

    // videos는 전체 교체 계약 — 예전엔 null=유지로 웹 업로드 동영상을 보호했지만, 지금은 앱 수정 폼이 기존 videoUrls를 채워 보낸다(규칙은 도메인 PostRepository.updatePost KDoc 참조)
    override suspend fun updatePost(
        groupId: Long,
        postId: Long,
        text: String,
        images: List<String>,
        videos: List<String>
    ): PostResponse =
        client.patch("/api/groups/$groupId/posts/$postId") {
            contentType(ContentType.Application.Json)
            setBody(UpdatePostRequest(text = text, images = images, videos = videos))
        }.body()

    override suspend fun deletePost(groupId: Long, postId: Long) {
        client.delete("/api/groups/$groupId/posts/$postId")
    }

    override suspend fun reportPost(groupId: Long, postId: Long, reason: String?) {
        client.post("/api/groups/$groupId/posts/$postId/report") {
            contentType(ContentType.Application.Json)
            setBody(ReportPostRequest(reason))
        }
    }

    override suspend fun getPostLikes(groupId: Long, postId: Long): List<PostLikeResponse> =
        client.get("/api/groups/$groupId/posts/$postId/likes").body()

    override suspend fun likePost(groupId: Long, postId: Long) {
        client.post("/api/groups/$groupId/posts/$postId/likes")
    }

    override suspend fun unlikePost(groupId: Long, postId: Long) {
        client.delete("/api/groups/$groupId/posts/$postId/likes")
    }

    override suspend fun getComments(groupId: Long, postId: Long): List<CommentResponse> =
        client.get("/api/groups/$groupId/posts/$postId/comments").body()

    override suspend fun createComment(groupId: Long, postId: Long, text: String, parentReplyId: Long?): CommentResponse =
        client.post("/api/groups/$groupId/posts/$postId/comments") {
            contentType(ContentType.Application.Json)
            setBody(CreateCommentRequest(text = text, parentReplyId = parentReplyId))
        }.body()

    override suspend fun deleteComment(groupId: Long, postId: Long, commentId: Long) {
        client.delete("/api/groups/$groupId/posts/$postId/comments/$commentId")
    }
}
