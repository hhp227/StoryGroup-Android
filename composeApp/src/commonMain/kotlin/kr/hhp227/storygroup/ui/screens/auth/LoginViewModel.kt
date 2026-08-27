package kr.hhp227.storygroup.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.shared.domain.usecase.IsLoggedInUseCase
import kr.hhp227.storygroup.shared.domain.usecase.LoginUseCase
import kr.hhp227.storygroup.shared.domain.usecase.LogoutUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel
import org.jetbrains.compose.resources.getString
import storygroup.composeapp.generated.resources.Res
import storygroup.composeapp.generated.resources.login_error

/** 세션 홀더 — iosApp LoginViewModel.swift와 1:1 미러(같은 UiState·Action·로직, shared 유스케이스 소비) */
class LoginViewModel(
    isLoggedInUseCase: IsLoggedInUseCase,
    private val loginUseCase: LoginUseCase,
    private val logoutUseCase: LogoutUseCase
) : ViewModel(), MviViewModel<LoginViewModel.UiState, LoginViewModel.Action, Nothing> {
    private val _uiState = MutableStateFlow(UiState(isLoggedIn = isLoggedInUseCase()))
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // 세션 전환은 일회성이 아니라 상태(isLoggedIn) — 이벤트 없음
    override val event: Flow<Nothing> = emptyFlow()

    override fun onAction(action: Action) {
        when (action) {
            is Action.Login -> login(action.email, action.password)
            Action.Logout -> logout()
            Action.ClearError -> _uiState.update { it.copy(error = null) }
        }
    }

    private fun login(email: String, password: String) {
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { loginUseCase(email, password) }
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, isLoggedIn = true) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: getString(Res.string.login_error)) }
                }
        }
    }

    private fun logout() {
        viewModelScope.launch {
            logoutUseCase()
            _uiState.update { it.copy(isLoggedIn = false) }
        }
    }

    data class UiState(
        val isLoading: Boolean = false,
        val isLoggedIn: Boolean = false,
        val error: String? = null
    )

    sealed interface Action {
        data class Login(val email: String, val password: String) : Action
        data object Logout : Action
        data object ClearError : Action
    }
}
