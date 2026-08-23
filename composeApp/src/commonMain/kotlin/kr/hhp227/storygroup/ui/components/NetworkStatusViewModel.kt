package kr.hhp227.storygroup.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kr.hhp227.storygroup.shared.domain.model.NetworkAlertState
import kr.hhp227.storygroup.shared.domain.usecase.ObserveNetworkAlertStateUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel

/**
 * 네트워크 연결 배너 상태 — 로그인 화면 포함 전역이라 세션이 아닌 앱 루트 스코프(App.kt viewModel {}).
 * 사용자 액션·일회성 이벤트가 없어 ACTION/EVENT 모두 Nothing.
 * iosApp NetworkStatusViewModel.swift와 1:1 미러
 */
class NetworkStatusViewModel(
    observeNetworkAlertStateUseCase: ObserveNetworkAlertStateUseCase
) : ViewModel(), MviViewModel<NetworkStatusViewModel.UiState, Nothing, Nothing> {
    private val _uiState = MutableStateFlow(UiState())
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    override val event: Flow<Nothing> = emptyFlow()

    override fun onAction(action: Nothing) = Unit

    init {
        // 구독 수명 = 앱 루트 VM 수명 — 프로세스가 살아 있는 동안 감지가 유지된다
        observeNetworkAlertStateUseCase()
            .onEach { state -> _uiState.update { it.copy(networkAlertState = state) } }
            .launchIn(viewModelScope)
    }

    data class UiState(
        val networkAlertState: NetworkAlertState = NetworkAlertState.hidden
    )
}
