package kr.hhp227.storygroup.shared.data.paging

import app.cash.paging.PagingConfig
import app.cash.paging.PagingSource
import app.cash.paging.PagingSourceLoadParams
import app.cash.paging.PagingSourceLoadResult
import app.cash.paging.PagingSourceLoadResultError
import app.cash.paging.PagingSourceLoadResultPage
import app.cash.paging.PagingState
import kotlin.coroutines.cancellation.CancellationException

/**
 * 서버 page/size 목록 공용 페이징 설정 — 계약과 1:1이 되게 initialLoadSize도 pageSize로 고정
 * (기본값 3배면 첫 로드 후 페이지 키가 서버 페이지와 어긋난다)
 */
internal val PagePagingConfig = PagingConfig(pageSize = 20, initialLoadSize = 20, enablePlaceholders = false)

/**
 * 서버 page/size 페이징을 Paging3 키로 변환하는 공용 로더(게시글 피드/그룹 목록 공용).
 * 데이터 소스는 [loadPage]로 주입 — 실패는 LoadResult.Error로 흘러 재시도와 통합된다.
 */
internal class PagePagingSource<T : Any>(
    private val loadPage: suspend (page: Int, size: Int) -> List<T>
) : PagingSource<Int, T>() {

    override suspend fun load(params: PagingSourceLoadParams<Int>): PagingSourceLoadResult<Int, T> = try {
        val page = params.key ?: 0
        val items = loadPage(page, params.loadSize)

        PagingSourceLoadResultPage(
            data = items,
            prevKey = if (page == 0) null else page - 1,
            nextKey = if (items.size < params.loadSize) null else page + 1
        )
    } catch (e: CancellationException) {
        // 취소는 오류가 아니다 — 삼키면 스트림 교체(refresh) 시 LoadState.Error로 오인 표시된다
        throw e
    } catch (e: Exception) {
        PagingSourceLoadResultError(e)
    }

    // 갱신은 항상 첫 페이지부터 — 최신순 목록이라 앵커 복원보다 최신 항목이 우선
    override fun getRefreshKey(state: PagingState<Int, T>): Int? = null
}
