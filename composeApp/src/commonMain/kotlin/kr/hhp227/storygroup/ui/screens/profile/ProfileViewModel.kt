package kr.hhp227.storygroup.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.shared.domain.model.Profile
import kr.hhp227.storygroup.shared.domain.usecase.GetMyProfileUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel
import org.jetbrains.compose.resources.getString
import storygroup.composeapp.generated.resources.Res
import storygroup.composeapp.generated.resources.profile_error_load

/** 내 정보(GET /api/users/me) — 프로필 화면/드로어 헤더가 공유한다. iosApp ProfileViewModel.swift와 1:1 미러 */
class ProfileViewModel(
    private val getMyProfileUseCase: GetMyProfileUseCase
) : ViewModel(), MviViewModel<ProfileViewModel.UiState, ProfileViewModel.Action, Nothing> {
    private val _uiState = MutableStateFlow(UiState())
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    override val event: Flow<Nothing> = emptyFlow()

    override fun onAction(action: Action) {
        when (action) {
            Action.Load -> load()
        }
    }

    /** 갱신 — 이전 값은 로딩 중에도 유지 */
    private fun load() {
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { getMyProfileUseCase() }
                .onSuccess { profile ->
                    _uiState.update { it.copy(isLoading = false, profile = profile) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: getString(Res.string.profile_error_load)) }
                }
        }
    }

    init {
        // 세션 스코프라 "생성 = 세션 진입 1회" — 여기서 바로 로드한다(재로그인 시 재생성)
        load()
    }

    data class UiState(
        val isLoading: Boolean = false,
        val profile: Profile? = null,
        val error: String? = null
    )

    sealed interface Action {
        data object Load : Action
    }
}
