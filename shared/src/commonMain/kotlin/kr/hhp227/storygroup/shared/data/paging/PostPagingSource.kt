package kr.hhp227.storygroup.shared.data.paging

import app.cash.paging.PagingConfig
import app.cash.paging.PagingSource
import app.cash.paging.PagingSourceLoadParams
import app.cash.paging.PagingSourceLoadResult
import app.cash.paging.PagingSourceLoadResultError
import app.cash.paging.PagingSourceLoadResultPage
import app.cash.paging.PagingState
import kr.hhp227.storygroup.shared.domain.model.Post

/**
 * 게시글 피드 공용 페이징 설정 — 서버 page/size 계약과 1:1이 되게 initialLoadSize도 pageSize로 고정
 * (기본값 3배면 첫 로드 후 페이지 키가 서버 페이지와 어긋난다)
 */
internal val PostPagingConfig = PagingConfig(pageSize = 20, initialLoadSize = 20, enablePlaceholders = false)

/**
 * 게시글 페이지 로더 — 서버 page/size 페이징을 Paging3 키로 변환(홈 라운지/그룹 상세 공용).
 * 데이터 소스(라운지 해석 포함)는 [loadPage]로 주입 — 실패는 LoadResult.Error로 흘러 재시도와 통합된다.
 */
internal class PostPagingSource(
    private val loadPage: suspend (page: Int, size: Int) -> List<Post>
) : PagingSource<Int, Post>() {

    override suspend fun load(params: PagingSourceLoadParams<Int>): PagingSourceLoadResult<Int, Post> = try {
        val page = params.key ?: 0
        val posts = loadPage(page, params.loadSize)

        PagingSourceLoadResultPage(
            data = posts,
            prevKey = if (page == 0) null else page - 1,
            nextKey = if (posts.size < params.loadSize) null else page + 1
        )
    } catch (e: Exception) {
        PagingSourceLoadResultError(e)
    }

    // 갱신은 항상 첫 페이지부터 — 최신순 피드라 앵커 복원보다 최신 글이 우선
    override fun getRefreshKey(state: PagingState<Int, Post>): Int? = null
}
