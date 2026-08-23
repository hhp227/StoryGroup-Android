package kr.hhp227.storygroup.shared.bridge

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.shared.domain.model.NetworkAlertState
import kr.hhp227.storygroup.shared.domain.usecase.ObserveNetworkAlertStateUseCase

/** 네트워크 배너 상태 Flow 대응 핸들 — PersonalEventFlowAdapter와 동일 규약(타입별 어댑터) */
class NetworkAlertStateFlowAdapter internal constructor(
    private val source: Flow<NetworkAlertState>
) {
    fun subscribe(onEach: (NetworkAlertState) -> Unit): FlowSubscription {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        scope.launch { source.collect { onEach(it) } }
        return FlowSubscription(scope)
    }
}

/** Kotlin의 observeNetworkAlertStateUseCase() 호출 대응 — Swift KotlinFlowPublisher가 감싼다 */
fun ObserveNetworkAlertStateUseCase.statesFlow(): NetworkAlertStateFlowAdapter =
    NetworkAlertStateFlowAdapter(invoke())
