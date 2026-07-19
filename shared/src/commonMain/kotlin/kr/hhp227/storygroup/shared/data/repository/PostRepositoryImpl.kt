package kr.hhp227.storygroup.shared.data.repository

import app.cash.paging.Pager
import app.cash.paging.PagingData
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.Flow
import kr.hhp227.storygroup.shared.data.network.dto.CreatePostRequest
import kr.hhp227.storygroup.shared.data.network.dto.PostResponse
import kr.hhp227.storygroup.shared.data.paging.PagePagingConfig
import kr.hhp227.storygroup.shared.data.paging.PagePagingSource
import kr.hhp227.storygroup.shared.domain.model.Group
import kr.hhp227.storygroup.shared.domain.model.Post
import kr.hhp227.storygroup.shared.domain.repository.GroupRepository
import kr.hhp227.storygroup.shared.domain.repository.PostRepository

// 라운지 피드는 그룹 목록에서 라운지를 찾아야 해서 GroupRepository에 의존한다(웹 메인 피드 미러)
class PostRepositoryImpl(
    private val client: HttpClient,
    private val groupRepository: GroupRepository
) : PostRepository {

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

    override suspend fun createPost(groupId: Long, text: String): Result<Post> =
        runCatching {
            client.post("/api/groups/$groupId/posts") {
                contentType(ContentType.Application.Json)
                setBody(CreatePostRequest(text = text))
            }.body<PostResponse>().toDomain()
        }

    override suspend fun createLoungePost(text: String): Result<Post> =
        runCatching { createPost(resolveLoungeId(), text).getOrThrow() }

    private suspend fun resolveLoungeId(): Long =
        groupRepository.getMyGroups().getOrThrow().firstOrNull(Group::isLounge)?.id
            ?: error("라운지를 찾을 수 없습니다.")
}

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
