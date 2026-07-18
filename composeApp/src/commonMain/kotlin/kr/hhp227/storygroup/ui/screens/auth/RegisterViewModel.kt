package kr.hhp227.storygroup.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.shared.domain.usecase.RegisterUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel

/**
 * 가입 성공 시 웹과 동일하게 로그인 화면으로 돌려보낸다(자동 로그인 안 함) — Event.Registered 일회성 발화.
 * iosApp RegisterViewModel.swift와 1:1 미러.
 */
class RegisterViewModel(
    private val registerUseCase: RegisterUseCase
) : ViewModel(), MviViewModel<RegisterViewModel.UiState, RegisterViewModel.Action, RegisterViewModel.Event> {
    private val _uiState = MutableStateFlow(UiState())
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _event = Channel<Event>(Channel.BUFFERED)
    override val event: Flow<Event> = _event.receiveAsFlow()

    override fun onAction(action: Action) {
        when (action) {
            is Action.Register -> register(action.name, action.email, action.password)
            Action.ClearError -> _uiState.update { it.copy(error = null) }
        }
    }

    private fun register(name: String, email: String, password: String) {
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { registerUseCase(name, email, password) }
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false) }
                    _event.trySend(Event.Registered)
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "가입에 실패했습니다.") }
                }
        }
    }

    data class UiState(
        val isLoading: Boolean = false,
        val error: String? = null
    )

    sealed interface Action {
        data class Register(val name: String, val email: String, val password: String) : Action
        data object ClearError : Action
    }

    sealed interface Event {
        data object Registered : Event
    }
}
