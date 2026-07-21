package kr.hhp227.storygroup.shared.bridge

import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.shared.domain.model.DiscoverGroup
import kr.hhp227.storygroup.shared.domain.model.DiscoverSort
import kr.hhp227.storygroup.shared.domain.model.Group
import kr.hhp227.storygroup.shared.domain.usecase.GetDiscoverGroupsPagingDataUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetMyGroupsPagingDataUseCase

// 그룹 목록의 Flow<PagingData<Group>> 브리지 — PostBridges.kt의 Group 타입 대응
// (ObjC 제네릭은 클로저 파라미터가 소거되어 Swift 타입 안전성이 깨지므로 타입별 어댑터를 둔다)

/** 그룹 목록의 Flow<PagingData> 대응 핸들 — PostPagingFlowAdapter와 동일 규약 */
class GroupPagingFlowAdapter internal constructor(
    private val source: Flow<PagingData<Group>>,
    private val cached: Boolean = false
) {
    fun cachedIn(): GroupPagingFlowAdapter = GroupPagingFlowAdapter(source, cached = true)

    fun subscribe(onEach: (PagingData<Group>) -> Unit): FlowSubscription {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val flow = if (cached) source.cachedIn(scope) else source

        scope.launch { flow.collect { onEach(it) } }
        return FlowSubscription(scope)
    }
}

/** Kotlin의 getMyGroupsPagingDataUseCase() 호출 대응 — Swift callAsFunction이 감싼다 */
fun GetMyGroupsPagingDataUseCase.pagingFlow(): GroupPagingFlowAdapter =
    GroupPagingFlowAdapter(invoke())

/** Swift State 기본값용 — Kotlin의 PagingData.empty() 대응 */
fun emptyGroupPagingData(): PagingData<Group> = PagingData.empty()

// 그룹 탐색 결과의 Flow<PagingData<DiscoverGroup>> 브리지 — DiscoverGroup 타입 대응(GroupPagingFlowAdapter와 동일 규약)

/** 그룹 탐색 결과의 Flow<PagingData> 대응 핸들 — GroupPagingFlowAdapter와 동일 규약 */
class DiscoverGroupPagingFlowAdapter internal constructor(
    private val source: Flow<PagingData<DiscoverGroup>>,
    private val cached: Boolean = false
) {
    fun cachedIn(): DiscoverGroupPagingFlowAdapter = DiscoverGroupPagingFlowAdapter(source, cached = true)

    fun subscribe(onEach: (PagingData<DiscoverGroup>) -> Unit): FlowSubscription {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val flow = if (cached) source.cachedIn(scope) else source

        scope.launch { flow.collect { onEach(it) } }
        return FlowSubscription(scope)
    }
}

/** Kotlin의 getDiscoverGroupsPagingDataUseCase(query, sort) 호출 대응 — Swift callAsFunction이 감싼다 */
fun GetDiscoverGroupsPagingDataUseCase.pagingFlow(query: String, sort: DiscoverSort): DiscoverGroupPagingFlowAdapter =
    DiscoverGroupPagingFlowAdapter(invoke(query, sort))

/** Swift State 기본값용 — Kotlin의 PagingData.empty() 대응 */
fun emptyDiscoverGroupPagingData(): PagingData<DiscoverGroup> = PagingData.empty()
