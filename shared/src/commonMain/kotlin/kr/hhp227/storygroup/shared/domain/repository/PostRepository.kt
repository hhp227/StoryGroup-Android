package kr.hhp227.storygroup.shared.domain.repository

import app.cash.paging.PagingData
import kotlinx.coroutines.flow.Flow
import kr.hhp227.storygroup.shared.domain.model.Post

interface PostRepository {
    /** 그룹 게시글 단건 페이지 — GET /api/groups/{id}/posts (페이징 스트림 내부에서 사용) */
    suspend fun getPosts(groupId: Long, page: Int, size: Int): Result<List<Post>>

    /** 그룹 피드 Paging 스트림 — cachedIn은 각 플랫폼 프레젠테이션 경계에서 적용한다 */
    fun getGroupPostsPagingData(groupId: Long): Flow<PagingData<Post>>

    /**
     * 라운지 피드 Paging 스트림 — 웹 메인 피드 미러. 라운지 해석은 PagingSource 로드 안에서
     * 수행되어 실패도 LoadState.Error로 흘러 재시도와 통합되고, refresh마다 재해석된다(재로그인 대응).
     */
    fun getLoungePostsPagingData(): Flow<PagingData<Post>>

    /**
     * 게시글 작성 — POST /api/groups/{id}/posts, 생성된 게시글을 돌려준다.
     * images는 [UploadImageUseCase]로 먼저 업로드해 받은 URL 목록 — 첨부 순서 그대로 서버에 전달된다.
     */
    suspend fun createPost(groupId: Long, text: String, images: List<String> = emptyList()): Result<Post>

    /** 라운지에 게시 — 홈 피드 작성 진입점(웹 메인 피드 폼 미러), 라운지 해석 포함 */
    suspend fun createLoungePost(text: String, images: List<String> = emptyList()): Result<Post>
}
