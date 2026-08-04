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
import kr.hhp227.storygroup.shared.domain.model.DiscoverGroup
import kr.hhp227.storygroup.shared.domain.model.Group
import kr.hhp227.storygroup.shared.domain.usecase.CancelJoinRequestUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetMyGroupsPagingDataUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetMyJoinRequestedGroupsUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel

/**
 * 그룹 탭 목록 — 레거시 user_groups 페이징 미러(라운지 제외는 shared 데이터 계층).
 * VM은 캐시(cachedIn)와 갱신 Event 발화만 담당 — 갱신은 화면이 Event를 받아 프레젠터
 * refresh()로 수행한다(홈 피드와 동일 패턴, 스트림 교체 없음).
 * 가입 신청중(PENDING) 그룹은 페이징과 별개의 소수 목록이라 일반 상태로 들고,
 * 진입/Refresh/신청·취소 신호(RefreshPending) 때 재조회한다.
 * 세션 스코프에 선언되어 "생성 = 세션 진입 1회"이므로 init에서 바로 시작한다(재로그인 시 재생성).
 * iosApp GroupsViewModel.swift와 1:1 미러
 */
class GroupsViewModel(
    getMyGroupsPagingDataUseCase: GetMyGroupsPagingDataUseCase,
    private val getMyJoinRequestedGroupsUseCase: GetMyJoinRequestedGroupsUseCase,
    private val cancelJoinRequestUseCase: CancelJoinRequestUseCase
) : ViewModel(), MviViewModel<GroupsViewModel.UiState, GroupsViewModel.Action, GroupsViewModel.Event> {
    private val _uiState = MutableStateFlow(UiState())
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<Event>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val event: Flow<Event> = _event.asSharedFlow()

    private fun setPagingData(pagingData: PagingData<Group>) {
        _uiState.update { it.copy(pagingData = pagingData) }
    }

    override fun onAction(action: Action) {
        when (action) {
            // 그룹 생성/가입 후 갱신용 — 화면이 refresh()로 목록을 첫 페이지부터 다시 읽는다
            Action.Refresh -> {
                _event.tryEmit(Event.Refresh)
                loadPendingGroups()
            }
            // 승인제 신청/신청 취소는 내 그룹 목록엔 영향이 없다 — 신청중 섹션만 재조회
            Action.RefreshPending -> loadPendingGroups()
            is Action.CancelRequest -> cancelRequest(action.groupId)
        }
    }

    private fun loadPendingGroups() {
        viewModelScope.launch {
            // 실패는 조용히 넘긴다 — 신청중 섹션은 부가 정보라 그룹 목록(페이징)까지 막지 않는다
            runCatching { getMyJoinRequestedGroupsUseCase() }
                .onSuccess { groups -> _uiState.update { it.copy(pendingGroups = groups, pendingError = null) } }
        }
    }

    private fun cancelRequest(groupId: Long) {
        if (_uiState.value.cancelingGroupId != null) return

        _uiState.update { it.copy(cancelingGroupId = groupId, pendingError = null) }
        viewModelScope.launch {
            runCatching { cancelJoinRequestUseCase(groupId) }
                .onSuccess {
                    // 서버 재조회 없이 낙관적으로 제거 — 실패했더라도 다음 Refresh 때 서버 상태로 수렴한다
                    _uiState.update { state ->
                        state.copy(
                            cancelingGroupId = null,
                            pendingGroups = state.pendingGroups.filterNot { it.id == groupId }
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(cancelingGroupId = null, pendingError = e.message ?: "신청 취소에 실패했습니다.") }
                }
        }
    }

    init {
        // UseCase는 cachedIn 없는 Flow를 반환하므로 프레젠테이션 경계인 여기서 캐시를 적용한다
        getMyGroupsPagingDataUseCase()
            .cachedIn(viewModelScope)
            .onEach(::setPagingData)
            .launchIn(viewModelScope)
        loadPendingGroups()
    }

    /**
     * 그룹 목록은 Paging 스트림의 최신 스냅샷 — 로딩/에러/추가 로드는 화면이 LoadState로 그린다.
     * 가입 신청중 목록은 탐색과 같은 모양(DiscoverGroup, membership=PENDING) — 비어 있으면 화면이 섹션을 숨긴다
     */
    data class UiState(
        val pagingData: PagingData<Group> = PagingData.empty(),
        val pendingGroups: List<DiscoverGroup> = emptyList(),
        // 신청 취소 버튼 로딩 표시용 — 동시에 하나만 처리
        val cancelingGroupId: Long? = null,
        // 신청 취소 실패 문구 — 신청중 섹션 안에서만 그린다
        val pendingError: String? = null
    )

    sealed interface Action {
        data object Refresh : Action
        data object RefreshPending : Action
        data class CancelRequest(val groupId: Long) : Action
    }

    sealed interface Event {
        data object Refresh : Event
    }
}
