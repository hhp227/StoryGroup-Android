package kr.hhp227.storygroup.ui.screens.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.paging.PagingData
import app.cash.paging.cachedIn
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * PagingData(Paging-CRUD 샘플 패턴). groupId만 받아 스스로 로드한다 — 목록이 페이징으로
 * 바뀌어 스냅샷 lookup이 불가하고, 딥링크 진입에도 대비된다(로드 전 group은 null).
 * 피드 갱신은 화면이 Event를 받아 프레젠터 refresh()로 수행한다(홈 피드와 동일 패턴).
 * iosApp GroupDetailViewModel.swift와 1:1 미러
 */
class GroupDetailViewModel(
    val groupId: Long,
    private val getGroupUseCase: GetGroupUseCase,
    private val getGroupMembersUseCase: GetGroupMembersUseCase,
    getGroupPostsPagingDataUseCase: GetGroupPostsPagingDataUseCase
) : ViewModel(), MviViewModel<GroupDetailViewModel.UiState, GroupDetailViewModel.Action, GroupDetailViewModel.Event> {
    private val _uiState = MutableStateFlow(UiState())
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<Event>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val event: Flow<Event> = _event.asSharedFlow()

    private fun setPagingData(pagingData: PagingData<Post>) {
        _uiState.update { it.copy(pagingData = pagingData) }
    }

    override fun onAction(action: Action) {
        when (action) {
            Action.Refresh -> refresh()
            // 글쓰기 성공 시 발화 — 화면이 refresh()로 피드를 첫 페이지부터 다시 읽는다
            Action.RefreshFeed -> _event.tryEmit(Event.RefreshFeed)
        }
    }

    /** 상세 진입 시 발화 — 그룹+멤버 로드(피드는 Pager가 자체 로드/재시도) */
    private fun refresh() {
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
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
        getGroupPostsPagingDataUseCase(groupId)
            .cachedIn(viewModelScope)
            .onEach(::setPagingData)
            .launchIn(viewModelScope)
    }

    data class UiState(
        // 로드 전 null — 화면은 그룹 정보 자리만 비워 두고 커버/피드를 먼저 그린다
        val group: Group? = null,
        val pagingData: PagingData<Post> = PagingData.empty(),
        val members: List<GroupMember> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null
    )

    sealed interface Action {
        data object Refresh : Action
        data object RefreshFeed : Action
    }

    sealed interface Event {
        data object RefreshFeed : Event
    }
}
