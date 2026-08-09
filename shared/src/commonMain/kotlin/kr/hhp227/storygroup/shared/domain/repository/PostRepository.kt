package kr.hhp227.storygroup.shared.domain.repository

import app.cash.paging.PagingData
import kotlinx.coroutines.flow.Flow
import kr.hhp227.storygroup.shared.domain.model.Comment
import kr.hhp227.storygroup.shared.domain.model.Post
import kr.hhp227.storygroup.shared.domain.model.PostLike

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

    /**
     * 게시글 단건 — GET /api/groups/{groupId}/posts/{postId}.
     * 상세 화면은 피드가 넘겨준 값을 그대로 쓰지 않고 여기서 다시 읽는다 — 피드가 캐시된 사이
     * 본문이 수정·삭제됐을 수 있고, 알림에서 곧바로 들어오는 경로도 있기 때문이다.
     */
    suspend fun getPost(groupId: Long, postId: Long): Result<Post>

    /**
     * 게시글 수정 — PATCH, 작성자 본인만(권한은 서버가 판정한다).
     * images는 폼이 들고 있는 목록을 그대로 보내 전체 교체한다 — 서버의 3상태(null=유지) 계약을
     * 쓰지 않아야 "사진을 지웠는데 그대로 남는" 경우가 생기지 않는다.
     */
    suspend fun updatePost(groupId: Long, postId: Long, text: String, images: List<String>): Result<Post>

    /** 게시글 삭제 — DELETE, 작성자 본인만(권한은 서버가 판정한다) */
    suspend fun deletePost(groupId: Long, postId: Long): Result<Unit>

    /**
     * 게시글이 수정됐다는 알림 — [updatePost] 성공 응답(최신 본문)을 그대로 흘린다.
     * 목록 화면이 이걸 받아 자기 PagingData 스냅샷에서 그 항목만 갈아끼운다 — refresh는
     * 첫 페이지부터 전체 재조회라 이미 쌓아둔 페이지와 스크롤 위치를 잃기 때문이다.
     * 값을 보관하지 않는 일회성 신호라, 이후 새로고침에는 서버 값이 그대로 이긴다.
     */
    val postUpdates: Flow<Post>

    /** 좋아요 누른 사람 목록 — 개수와 "내가 눌렀는지"를 이 목록에서 파생한다(웹 미러) */
    suspend fun getPostLikes(groupId: Long, postId: Long): Result<List<PostLike>>

    /** 좋아요 설정/해제 — liked=true면 POST, false면 DELETE */
    suspend fun setPostLiked(groupId: Long, postId: Long, liked: Boolean): Result<Unit>

    /** 댓글·답글 전체 목록(평평한 한 목록, parentReplyId로 계층 구분) */
    suspend fun getComments(groupId: Long, postId: Long): Result<List<Comment>>

    /** 댓글 작성 — parentReplyId를 주면 그 댓글의 답글이 된다 */
    suspend fun createComment(groupId: Long, postId: Long, text: String, parentReplyId: Long? = null): Result<Comment>

    /** 댓글 삭제 — 작성자 본인만(권한은 서버가 판정한다) */
    suspend fun deleteComment(groupId: Long, postId: Long, commentId: Long): Result<Unit>
}
