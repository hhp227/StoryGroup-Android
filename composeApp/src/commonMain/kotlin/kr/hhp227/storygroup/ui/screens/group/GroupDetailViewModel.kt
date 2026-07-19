package kr.hhp227.storygroup.ui.screens.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.paging.PagingData
import app.cash.paging.cachedIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.shared.domain.model.Group
import kr.hhp227.storygroup.shared.domain.model.GroupMember
import kr.hhp227.storygroup.shared.domain.model.Post
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupMembersUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupPostsPagingDataUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel

/**
 * 그룹 상세 — 웹 /groups/[id] 미러. 커버+멤버는 UiState 필드, 피드는 UiState에 담기는 최신
 * PagingData(Paging-CRUD 샘플 패턴). 목록에서 받은 그룹으로 즉시 그리고 Refresh에서 신선화한다.
 * iosApp GroupDetailViewModel.swift와 1:1 미러
 */
class GroupDetailViewModel(
    initialGroup: Group,
    private val getGroupUseCase: GetGroupUseCase,
    private val getGroupMembersUseCase: GetGroupMembersUseCase,
    getGroupPostsPagingDataUseCase: GetGroupPostsPagingDataUseCase
) : ViewModel(), MviViewModel<GroupDetailViewModel.UiState, GroupDetailViewModel.Action, Nothing> {
    private val _uiState = MutableStateFlow(UiState(group = initialGroup))
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    override val event: Flow<Nothing> = emptyFlow()

    private fun setPagingData(pagingData: PagingData<Post>) {
        _uiState.update { it.copy(pagingData = pagingData) }
    }

    override fun onAction(action: Action) {
        when (action) {
            Action.Refresh -> refresh()
        }
    }

    /** 상세 진입 시 발화 — 그룹 신선화+멤버(피드는 Pager가 자체 로드/재시도) */
    private fun refresh() {
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val groupId = _uiState.value.group.id

            runCatching {
                val group = getGroupUseCase(groupId)
                val members = getGroupMembersUseCase(groupId)
                group to members
            }.onSuccess { (group, members) ->
                _uiState.update { it.copy(isLoading = false, group = group, members = members) }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "그룹을 불러오지 못했습니다.")
                }
            }
        }
    }

    init {
        // UseCase는 cachedIn 없는 Flow를 반환하므로 프레젠테이션 경계인 여기서 캐시를 적용한다
        getGroupPostsPagingDataUseCase(initialGroup.id)
            .cachedIn(viewModelScope)
            .onEach(::setPagingData)
            .launchIn(viewModelScope)
    }

    data class UiState(
        val group: Group,
        val pagingData: PagingData<Post> = PagingData.empty(),
        val members: List<GroupMember> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null
    )

    sealed interface Action {
        data object Refresh : Action
    }
}
