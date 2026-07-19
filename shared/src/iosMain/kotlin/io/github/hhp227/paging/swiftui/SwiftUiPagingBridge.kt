// Jetpack-Paging-for-SwiftUI kmp/paging-swiftui에서 벤더링(0.3.0) — io.github.hhp227:paging-swiftui가
// Maven Central 미배포+iOS 타깃은 Mac에서만 빌드 가능해서 소스로 가져옴. 배포되면 의존성으로 교체할 것.
// ⚠️원본은 paging-common 3.3.3+의 PagingDataPresenter 기반인데, 이 프로젝트는 cash 포크가 번들한
// 3.3.0-alpha02에 묶여 있어(paging-compose-common이 PagingDataDiffer를 참조) 같은 시대 API인
// PagingDataDiffer로 조정했다. 공개 표면(count/start/item/...)은 원본과 동일하다.
package io.github.hhp227.paging.swiftui

import androidx.paging.DifferCallback
import androidx.paging.NullPaddedList
import androidx.paging.PagingData
import androidx.paging.PagingDataDiffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * androidx paging-common의 [PagingDataDiffer]를 Swift(SwiftUI)에서 다루기 좋은
 * 평탄한 API로 감싼 브리지.
 *
 * iOS 앱은 shared 프레임워크로 노출된 이 클래스를 Jetpack-Paging-for-SwiftUI(SPM)의
 * `PagingBridgeDataSource` 프로토콜에 어댑팅해서 `LazyPagingItems(bridge:)`로 주입한다.
 * 어댑터 샘플은 저장소의 docs/KMP.md 참고.
 *
 * 주의: Swift 쪽이 [onPagesUpdated]/[onLoadStatesUpdated]를 연결한 **뒤에** [start]를
 * 호출해야 초기 이벤트가 유실되지 않는다. 이 클래스는 [start] 전에는 아무것도 방출하지 않는다.
 */
class SwiftUiPagingBridge<T : Any>(
    private val pagingDataFlow: Flow<PagingData<T>>,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    /** 표시 목록이 갱신될 때마다 메인 스레드에서 호출된다 */
    var onPagesUpdated: (() -> Unit)? = null

    /** 로드 상태가 바뀔 때마다 호출된다 */
    var onLoadStatesUpdated: ((BridgeCombinedLoadStates) -> Unit)? = null

    private var started = false

    // paging-compose(alpha02) LazyPagingItems와 동일한 패턴 — 페이지 변경 콜백마다 목록 갱신을 알린다
    private val differCallback = object : DifferCallback {
        override fun onChanged(position: Int, count: Int) {
            onPagesUpdated?.invoke()
        }

        override fun onInserted(position: Int, count: Int) {
            onPagesUpdated?.invoke()
        }

        override fun onRemoved(position: Int, count: Int) {
            onPagesUpdated?.invoke()
        }
    }

    private val presenter = object : PagingDataDiffer<T>(differCallback, Dispatchers.Main) {
        override suspend fun presentNewList(
            previousList: NullPaddedList<T>,
            newList: NullPaddedList<T>,
            lastAccessedIndex: Int,
            onListPresentable: () -> Unit
        ): Int? {
            onListPresentable()
            onPagesUpdated?.invoke()
            return null
        }
    }

    /** 현재 표시 가능한 아이템 수 (presenter.size) */
    val count: Int get() = presenter.size

    /** PagingData 수집 시작. 콜백 연결이 끝난 뒤 한 번만 호출한다 (중복 호출은 무시) */
    fun start() {
        if (started) return
        started = true

        scope.launch {
            pagingDataFlow.collectLatest { presenter.collectFrom(it) }
        }
        scope.launch {
            presenter.loadStateFlow.filterNotNull().collect {
                onLoadStatesUpdated?.invoke(it.toBridge())
            }
        }
    }

    /** 아이템 반환 + 로드 힌트 트리거. 행이 화면에 나타날 때(onAppear) 메인 스레드에서 호출한다 */
    fun item(index: Int): T? = presenter[index]

    /** 힌트 없이 아이템만 반환 */
    fun peekItem(index: Int): T? = presenter.peek(index)

    /** 현재 표시 목록의 스냅샷 */
    fun snapshotItems(): List<T> = presenter.snapshot().items

    fun refresh() = presenter.refresh()

    fun retry() = presenter.retry()

    /** 수집 중단. Swift 어댑터의 deinit에서 호출한다 */
    fun dispose() {
        scope.cancel()
    }
}
