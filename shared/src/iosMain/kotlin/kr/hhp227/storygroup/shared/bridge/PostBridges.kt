package kr.hhp227.storygroup.shared.bridge

import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import io.github.hhp227.paging.swiftui.SwiftUiPagingBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.shared.domain.model.Post
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupPostsPagingDataUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetLoungePostsPagingDataUseCase
import kr.hhp227.storygroup.shared.domain.usecase.ObservePostUpdatesUseCase

// Kotlin Flow ↔ Combine Publisher 대응 계층 (Paging-CRUD 샘플과 동일 패턴).
// Swift 쪽(KmpInterop.swift)이 이 핸들들을 Combine Publisher로 감싸서,
// SwiftUI ViewModel이 Compose ViewModel과 1:1 코드 패턴을 갖게 한다.

/** Combine Subscription이 cancel을 위임하는 구독 핸들 */
class FlowSubscription internal constructor(private val scope: CoroutineScope) {
    fun cancel() {
        scope.cancel()
    }
}

/**
 * 게시글 피드의 Flow<PagingData> 대응 핸들. cachedIn()은 Kotlin의 cachedIn(viewModelScope)
 * 대응으로, 실제 캐시는 구독 시점에 만들어지는 구독 수명 스코프에 적용된다
 * (Swift ViewModel의 cancellables 수명 == viewModelScope 수명).
 */
class PostPagingFlowAdapter internal constructor(
    private val source: Flow<PagingData<Post>>,
    private val cached: Boolean = false
) {
    fun cachedIn(): PostPagingFlowAdapter = PostPagingFlowAdapter(source, cached = true)

    fun subscribe(onEach: (PagingData<Post>) -> Unit): FlowSubscription {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val flow = if (cached) source.cachedIn(scope) else source

        scope.launch { flow.collect { onEach(it) } }
        return FlowSubscription(scope)
    }
}

/** Kotlin의 getLoungePostsPagingDataUseCase() 호출 대응 — Swift callAsFunction이 감싼다 */
fun GetLoungePostsPagingDataUseCase.pagingFlow(): PostPagingFlowAdapter =
    PostPagingFlowAdapter(invoke())

/** Kotlin의 getGroupPostsPagingDataUseCase(groupId) 호출 대응 — Swift callAsFunction이 감싼다 */
fun GetGroupPostsPagingDataUseCase.pagingFlow(groupId: Long): PostPagingFlowAdapter =
    PostPagingFlowAdapter(invoke(groupId))

/**
 * Swift의 Publisher.collectAsLazyPagingItems()가 사용하는 push형 브리지.
 * State에서 흘러나온 PagingData(Combine 퍼블리셔)를 presenter(SwiftUiPagingBridge)로 전달한다.
 */
class PagingDataSubject<T : Any> {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val pagingDataFlow = MutableSharedFlow<PagingData<T>>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val bridge: SwiftUiPagingBridge<T> = SwiftUiPagingBridge(pagingDataFlow, scope)

    fun send(pagingData: PagingData<T>) {
        pagingDataFlow.tryEmit(pagingData)
    }
}

/** Swift State 기본값용 — Kotlin의 PagingData.empty() 대응 */
fun emptyPostPagingData(): PagingData<Post> = PagingData.empty()

/** 게시글 수정 알림 Flow 대응 핸들 — PersonalEventFlowAdapter와 동일 규약(타입별 어댑터) */
class PostUpdateFlowAdapter internal constructor(
    private val source: Flow<Post>
) {
    fun subscribe(onEach: (Post) -> Unit): FlowSubscription {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        scope.launch { source.collect { onEach(it) } }
        return FlowSubscription(scope)
    }
}

/** Kotlin의 observePostUpdatesUseCase() 호출 대응 — Swift callAsFunction이 감싼다 */
fun ObservePostUpdatesUseCase.updatesFlow(): PostUpdateFlowAdapter = PostUpdateFlowAdapter(invoke())

/**
 * Kotlin의 pagingData.map { if (it.id == post.id) post else it } 대응 —
 * PagingData.map의 transform은 suspend라 Swift 클로저를 넘길 수 없어 브리지 함수로 제공한다.
 */
fun postPagingDataWithUpdate(pagingData: PagingData<Post>, post: Post): PagingData<Post> =
    pagingData.map { if (it.id == post.id) post else it }
