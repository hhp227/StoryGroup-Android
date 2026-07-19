package kr.hhp227.storygroup.ui.screens.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.shared.domain.model.Group
import kr.hhp227.storygroup.shared.domain.usecase.GetMyGroupsUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel

/**
 * 그룹 탭 목록 — 웹 /groups 내 그룹 탭 미러(라운지 제외).
 * iosApp GroupsViewModel.swift와 1:1 미러
 */
class GroupsViewModel(
    private val getMyGroupsUseCase: GetMyGroupsUseCase
) : ViewModel(), MviViewModel<GroupsViewModel.UiState, GroupsViewModel.Action, Nothing> {
    private val _uiState = MutableStateFlow(UiState())
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    override val event: Flow<Nothing> = emptyFlow()

    override fun onAction(action: Action) {
        when (action) {
            Action.Refresh -> refresh()
        }
    }

    /** 로그인 세션 진입/재진입 시 발화 — 이전 목록은 로딩 중에도 유지 */
    private fun refresh() {
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { getMyGroupsUseCase() }
                .onSuccess { groups ->
                    // 라운지는 홈 탭이 담당 — 웹 내 그룹 목록과 동일하게 제외
                    _uiState.update {
                        it.copy(isLoading = false, groups = groups.filterNot(Group::isLounge))
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = e.message ?: "그룹 목록을 불러오지 못했습니다.")
                    }
                }
        }
    }

    data class UiState(
        val isLoading: Boolean = false,
        val groups: List<Group> = emptyList(),
        val error: String? = null
    )

    sealed interface Action {
        data object Refresh : Action
    }
}
