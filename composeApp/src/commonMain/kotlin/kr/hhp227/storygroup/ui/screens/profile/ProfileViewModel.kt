package kr.hhp227.storygroup.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.shared.domain.model.Profile
import kr.hhp227.storygroup.shared.domain.usecase.GetMyProfileUseCase

/** 내 정보(GET /api/users/me) — 프로필 화면/드로어 헤더가 공유한다. iosApp ProfileViewModel.swift와 1:1 미러 */
class ProfileViewModel(
    private val getMyProfileUseCase: GetMyProfileUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** 로그인 직후 호출 — 재로그인 시에도 항상 새로 가져온다(이전 값은 로딩 중에도 유지) */
    fun load() {
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { getMyProfileUseCase() }
                .onSuccess { profile ->
                    _uiState.update { it.copy(isLoading = false, profile = profile) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "내 정보를 불러오지 못했습니다.") }
                }
        }
    }

    data class UiState(
        val isLoading: Boolean = false,
        val profile: Profile? = null,
        val error: String? = null
    )
}
