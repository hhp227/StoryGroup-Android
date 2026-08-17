package kr.hhp227.storygroup.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.shared.domain.model.BlockedUser
import kr.hhp227.storygroup.shared.domain.usecase.GetBlockedUsersUseCase
import kr.hhp227.storygroup.shared.domain.usecase.UnblockUserUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel

/**
 * 차단 사용자 관리 — 웹 /settings/blocked 미러(목록+해제). 해제 성공은 목록에서 그 행만
 * 제거한다(웹과 동일 — 재조회 없음). 화면 전환이 없어 EVENT는 Nothing.
 * iosApp BlockedUsersViewModel.swift와 1:1 미러
 */
class BlockedUsersViewModel(
    private val getBlockedUsersUseCase: GetBlockedUsersUseCase,
    private val unblockUserUseCase: UnblockUserUseCase
) : ViewModel(), MviViewModel<BlockedUsersViewModel.UiState, BlockedUsersViewModel.Action, Nothing> {
    private val _uiState = MutableStateFlow(UiState())
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    override val event: Flow<Nothing> = emptyFlow()

    override fun onAction(action: Action) {
        when (action) {
            Action.Refresh -> refresh()
            is Action.Unblock -> unblock(action.userId)
            Action.DismissActionError -> _uiState.update { it.copy(actionError = null) }
        }
    }

    private fun refresh() {
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            runCatching { getBlockedUsersUseCase() }
                .onSuccess { blocked ->
                    _uiState.update { it.copy(isLoading = false, blocked = blocked) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoading = false, loadError = e.message ?: "차단 목록을 불러오지 못했습니다.")
                    }
                }
        }
    }

    private fun unblock(userId: Long) {
        if (_uiState.value.busyUserId != null) return

        _uiState.update { it.copy(busyUserId = userId, actionError = null) }
        viewModelScope.launch {
            runCatching { unblockUserUseCase(userId) }
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(busyUserId = null, blocked = state.blocked?.filter { it.userId != userId })
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(busyUserId = null, actionError = e.message ?: "차단 해제에 실패했습니다.") }
                }
        }
    }

    init {
        refresh()
    }

    data class UiState(
        // null=아직 로드 전 — 빈 목록(차단 없음)과 구분한다
        val blocked: List<BlockedUser>? = null,
        val isLoading: Boolean = false,
        val loadError: String? = null,
        // 해제 진행 중인 행 — 그 행의 버튼만 잠근다(웹 busyFor 미러)
        val busyUserId: Long? = null,
        val actionError: String? = null
    )

    sealed interface Action {
        data object Refresh : Action
        data class Unblock(val userId: Long) : Action
        data object DismissActionError : Action
    }
}
