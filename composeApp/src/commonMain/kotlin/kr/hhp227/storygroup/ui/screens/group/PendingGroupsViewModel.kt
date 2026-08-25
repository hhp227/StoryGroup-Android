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
import kr.hhp227.storygroup.shared.domain.model.DiscoverGroup
import kr.hhp227.storygroup.shared.domain.usecase.CancelJoinRequestUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetMyJoinRequestedGroupsUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel

/**
 * 가입 신청중 화면 — 레거시 JoinRequestGroupFragment 미러(내 그룹 안 섹션에서 독립 화면으로 분리).
 * 백스택 엔트리 스코프(방문마다 init 로드 — DiscoverGroups와 동일)라 신청/취소 후 재조회 신호가 필요 없다.
 * iosApp PendingGroupsViewModel.swift와 1:1 미러
 */
class PendingGroupsViewModel(
    private val getMyJoinRequestedGroupsUseCase: GetMyJoinRequestedGroupsUseCase,
    private val cancelJoinRequestUseCase: CancelJoinRequestUseCase
) : ViewModel(), MviViewModel<PendingGroupsViewModel.UiState, PendingGroupsViewModel.Action, Nothing> {
    private val _uiState = MutableStateFlow(UiState())
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    override val event: Flow<Nothing> = emptyFlow()

    override fun onAction(action: Action) {
        when (action) {
            Action.Refresh -> load()
            is Action.CancelRequest -> cancelRequest(action.groupId)
        }
    }

    private fun load() {
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            runCatching { getMyJoinRequestedGroupsUseCase() }
                .onSuccess { groups -> _uiState.update { it.copy(isLoading = false, groups = groups) } }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, loadError = e.message ?: "가입 신청중 그룹을 불러오지 못했습니다.") }
                }
        }
    }

    private fun cancelRequest(groupId: Long) {
        if (_uiState.value.cancelingGroupId != null) return

        _uiState.update { it.copy(cancelingGroupId = groupId, cancelError = null) }
        viewModelScope.launch {
            runCatching { cancelJoinRequestUseCase(groupId) }
                .onSuccess {
                    // 서버 재조회 없이 낙관적으로 제거 — 실패했더라도 다음 Refresh 때 서버 상태로 수렴한다
                    _uiState.update { state ->
                        state.copy(
                            cancelingGroupId = null,
                            groups = state.groups.filterNot { it.id == groupId }
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(cancelingGroupId = null, cancelError = e.message ?: "신청 취소에 실패했습니다.") }
                }
        }
    }

    init {
        load()
    }

    /**
     * 목록은 탐색과 같은 모양(DiscoverGroup, membership=PENDING).
     * 독립 화면이므로 로드 실패(loadError)는 재시도 상태로, 취소 실패(cancelError)는 목록 위 한 줄로 나눠 그린다
     */
    data class UiState(
        val groups: List<DiscoverGroup> = emptyList(),
        val isLoading: Boolean = false,
        val loadError: String? = null,
        val cancelError: String? = null,
        // 신청 취소 버튼 로딩 표시용 — 동시에 하나만 처리
        val cancelingGroupId: Long? = null
    )

    sealed interface Action {
        data object Refresh : Action
        data class CancelRequest(val groupId: Long) : Action
    }
}
