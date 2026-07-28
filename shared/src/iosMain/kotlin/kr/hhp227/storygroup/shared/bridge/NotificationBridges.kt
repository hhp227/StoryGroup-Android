package kr.hhp227.storygroup.shared.bridge

import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.shared.domain.model.AppNotification
import kr.hhp227.storygroup.shared.domain.model.PersonalEvent
import kr.hhp227.storygroup.shared.domain.usecase.GetNotificationsPagingDataUseCase
import kr.hhp227.storygroup.shared.domain.usecase.ObservePersonalEventsUseCase

// 알림 목록의 Flow<PagingData<AppNotification>> 브리지 — GroupBridges.kt의 타입별 어댑터와 동일 규약
// (ObjC 제네릭은 클로저 파라미터가 소거되어 Swift 타입 안전성이 깨지므로 타입별 어댑터를 둔다)

/** 알림 목록의 Flow<PagingData> 대응 핸들 — GroupPagingFlowAdapter와 동일 규약 */
class AppNotificationPagingFlowAdapter internal constructor(
    private val source: Flow<PagingData<AppNotification>>,
    private val cached: Boolean = false
) {
    fun cachedIn(): AppNotificationPagingFlowAdapter = AppNotificationPagingFlowAdapter(source, cached = true)

    fun subscribe(onEach: (PagingData<AppNotification>) -> Unit): FlowSubscription {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val flow = if (cached) source.cachedIn(scope) else source

        scope.launch { flow.collect { onEach(it) } }
        return FlowSubscription(scope)
    }
}

/** Kotlin의 getNotificationsPagingDataUseCase() 호출 대응 — Swift callAsFunction이 감싼다 */
fun GetNotificationsPagingDataUseCase.pagingFlow(): AppNotificationPagingFlowAdapter =
    AppNotificationPagingFlowAdapter(invoke())

/** Swift State 기본값용 — Kotlin의 PagingData.empty() 대응 */
fun emptyAppNotificationPagingData(): PagingData<AppNotification> = PagingData.empty()

/** 개인 큐 실시간 이벤트 Flow 대응 핸들 — ChatEventFlowAdapter와 동일 규약(타입별 어댑터) */
class PersonalEventFlowAdapter internal constructor(
    private val source: Flow<PersonalEvent>
) {
    fun subscribe(onEach: (PersonalEvent) -> Unit): FlowSubscription {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        scope.launch { source.collect { onEach(it) } }
        return FlowSubscription(scope)
    }
}

/** Kotlin의 observePersonalEventsUseCase() 호출 대응 — Swift callAsFunction이 감싼다 */
fun ObservePersonalEventsUseCase.eventsFlow(): PersonalEventFlowAdapter =
    PersonalEventFlowAdapter(invoke())
