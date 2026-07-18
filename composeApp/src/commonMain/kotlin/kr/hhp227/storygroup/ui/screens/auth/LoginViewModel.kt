package kr.hhp227.storygroup.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.shared.domain.usecase.IsLoggedInUseCase
import kr.hhp227.storygroup.shared.domain.usecase.LoginUseCase
import kr.hhp227.storygroup.shared.domain.usecase.LogoutUseCase

/** 세션 홀더 — iosApp LoginViewModel.swift와 1:1 미러(같은 UiState·로직, shared 유스케이스 소비) */
class LoginViewModel(
    isLoggedInUseCase: IsLoggedInUseCase,
    private val loginUseCase: LoginUseCase,
    private val logoutUseCase: LogoutUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(UiState(isLoggedIn = isLoggedInUseCase()))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun login(email: String, password: String) {
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { loginUseCase(email, password) }
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, isLoggedIn = true) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "로그인에 실패했습니다.") }
                }
        }
    }

    fun logout() {
        viewModelScope.launch {
            logoutUseCase()
            _uiState.update { it.copy(isLoggedIn = false) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    data class UiState(
        val isLoading: Boolean = false,
        val isLoggedIn: Boolean = false,
        val error: String? = null
    )
}
