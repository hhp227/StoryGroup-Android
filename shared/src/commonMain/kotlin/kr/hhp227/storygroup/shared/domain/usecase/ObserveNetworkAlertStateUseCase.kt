package kr.hhp227.storygroup.shared.domain.usecase

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kr.hhp227.storygroup.shared.domain.model.NetworkAlertState
import kr.hhp227.storygroup.shared.domain.repository.NetworkStatusRepository

/**
 * 연결 스트림 → 배너 상태 매핑(ConCafe 이식): 오프라인=지속 표시, 복구=1.8초 표시 후 숨김.
 * 복구 표시 중(delay) 재끊김은 delay 뒤로 밀린다 — ConCafe와 동일한 알려진 특성(스펙 §6).
 */
class ObserveNetworkAlertStateUseCase(
    private val networkStatusRepository: NetworkStatusRepository
) {
    operator fun invoke(): Flow<NetworkAlertState> {
        return flow {
            var previousIsConnected: Boolean? = null

            networkStatusRepository.observeIsConnected()
                .distinctUntilChanged()
                .collect { isConnected ->
                    if (isConnected) {
                        if (previousIsConnected == false) {
                            emit(NetworkAlertState.recovered)
                            delay(RECOVERED_MESSAGE_DURATION_MS)
                            emit(NetworkAlertState.hidden)
                        } else {
                            emit(NetworkAlertState.hidden)
                        }
                    } else {
                        emit(NetworkAlertState.offline)
                    }
                    previousIsConnected = isConnected
                }
        }
    }

    private companion object {
        const val RECOVERED_MESSAGE_DURATION_MS = 1_800L
    }
}
