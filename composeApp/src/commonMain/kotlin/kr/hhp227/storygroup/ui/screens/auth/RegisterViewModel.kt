package kr.hhp227.storygroup.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.shared.domain.usecase.RegisterUseCase

/**
 * 가입 성공 시 웹과 동일하게 로그인 화면으로 돌려보낸다(자동 로그인 안 함).
 * iosApp RegisterViewModel.swift와 1:1 미러.
 */
class RegisterViewModel(
    private val registerUseCase: RegisterUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun register(name: String, email: String, password: String) {
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { registerUseCase(name, email, password) }
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, isRegistered = true) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "가입에 실패했습니다.") }
                }
        }
    }

    /** 가입 완료 이벤트를 소비한다 — 화면 전환 후 재진입 시 중복 발화 방지 */
    fun consumeRegistered() {
        _uiState.update { it.copy(isRegistered = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    data class UiState(
        val isLoading: Boolean = false,
        val isRegistered: Boolean = false,
        val error: String? = null
    )
}
