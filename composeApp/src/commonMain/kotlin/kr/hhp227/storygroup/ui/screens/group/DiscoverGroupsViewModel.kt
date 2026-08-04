package kr.hhp227.storygroup.ui.screens.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.paging.PagingData
import app.cash.paging.cachedIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.shared.domain.model.DiscoverGroup
import kr.hhp227.storygroup.shared.domain.model.DiscoverSort
import kr.hhp227.storygroup.shared.domain.model.GroupMembershipStatus
import kr.hhp227.storygroup.shared.domain.model.JoinResult
import kr.hhp227.storygroup.shared.domain.usecase.CancelJoinRequestUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetDiscoverGroupsPagingDataUseCase
import kr.hhp227.storygroup.shared.domain.usecase.JoinGroupByCodeUseCase
import kr.hhp227.storygroup.shared.domain.usecase.JoinGroupUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel

/**
 * 그룹 찾기 — 검색어/정렬이 바뀔 때만 새 Pager를 구독하는 표준 Paging3 검색 패턴
 * (매 세션 진입마다 새로 트리거되던 예전 버그와 달리, 여기선 사용자가 조건을 바꿀 때만 재구독한다).
 * 가입/신청 직후엔 전체 목록을 새로고침하지 않고 localOverrides로 카드 상태만 낙관적으로 갱신해
 * 스크롤 위치를 보존한다. 가입 완료(JOINED) 시 Event.Joined를 발화해 화면이 세션 GroupsViewModel을
 * 갱신하게 한다(내 그룹 탭 반영).
 * iosApp DiscoverGroupsViewModel.swift와 1:1 미러
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverGroupsViewModel(
    private val getDiscoverGroupsPagingDataUseCase: GetDiscoverGroupsPagingDataUseCase,
    private val joinGroupUseCase: JoinGroupUseCase,
    private val joinGroupByCodeUseCase: JoinGroupByCodeUseCase,
    private val cancelJoinRequestUseCase: CancelJoinRequestUseCase
) : ViewModel(), MviViewModel<DiscoverGroupsViewModel.UiState, DiscoverGroupsViewModel.Action, DiscoverGroupsViewModel.Event> {
    private val _uiState = MutableStateFlow(UiState())
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<Event>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val event: Flow<Event> = _event.asSharedFlow()

    private fun setPagingData(pagingData: PagingData<DiscoverGroup>) {
        _uiState.update { it.copy(pagingData = pagingData) }
    }

    override fun onAction(action: Action) {
        when (action) {
            is Action.Search -> _uiState.update { it.copy(query = action.query) }
            is Action.ChangeSort -> _uiState.update { it.copy(sort = action.sort) }
            is Action.Join -> join(action.groupId)
            is Action.JoinByCode -> joinByCode(action.code)
            Action.DismissJoinByCodeError -> _uiState.update { it.copy(joinByCodeError = null) }
            is Action.CancelRequest -> cancelRequest(action.groupId)
        }
    }

    private fun join(groupId: Long) {
        if (_uiState.value.joiningGroupId != null) return

        _uiState.update { it.copy(joiningGroupId = groupId, error = null) }
        viewModelScope.launch {
            runCatching { joinGroupUseCase(groupId) }
                .onSuccess { result ->
                    val status = when (result.status) {
                        JoinResult.JOINED -> GroupMembershipStatus.MEMBER
                        JoinResult.REQUESTED -> GroupMembershipStatus.PENDING
                    }
                    _uiState.update {
                        it.copy(joiningGroupId = null, localOverrides = it.localOverrides + (groupId to status))
                    }
                    // 즉시 가입은 내 그룹 목록까지, 승인제 신청은 신청중 섹션만 갱신하면 된다
                    _event.tryEmit(if (result.status == JoinResult.JOINED) Event.Joined else Event.MembershipChanged)
                }
                .onFailure { e ->
                    _uiState.update { it.copy(joiningGroupId = null, error = e.message ?: "가입에 실패했습니다.") }
                }
        }
    }

    /** 초대코드 가입 — 승인제와 무관하게 즉시 MEMBER, 목록에 있으면 카드 상태도 낙관 갱신한다 */
    private fun joinByCode(code: String) {
        val trimmed = code.trim().uppercase()

        if (trimmed.isEmpty() || _uiState.value.isJoiningByCode) return

        _uiState.update { it.copy(isJoiningByCode = true, joinByCodeError = null) }
        viewModelScope.launch {
            runCatching { joinGroupByCodeUseCase(trimmed) }
                .onSuccess { group ->
                    _uiState.update {
                        it.copy(
                            isJoiningByCode = false,
                            localOverrides = it.localOverrides + (group.id to GroupMembershipStatus.MEMBER)
                        )
                    }
                    _event.tryEmit(Event.JoinedByCode)
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isJoiningByCode = false, joinByCodeError = e.message ?: "초대 코드 가입에 실패했습니다.")
                    }
                }
        }
    }

    private fun cancelRequest(groupId: Long) {
        if (_uiState.value.joiningGroupId != null) return

        _uiState.update { it.copy(joiningGroupId = groupId, error = null) }
        viewModelScope.launch {
            runCatching { cancelJoinRequestUseCase(groupId) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            joiningGroupId = null,
                            localOverrides = it.localOverrides + (groupId to GroupMembershipStatus.NONE)
                        )
                    }
                    _event.tryEmit(Event.MembershipChanged)
                }
                .onFailure { e ->
                    _uiState.update { it.copy(joiningGroupId = null, error = e.message ?: "신청 취소에 실패했습니다.") }
                }
        }
    }

    init {
        uiState
            .map { it.query to it.sort }
            .distinctUntilChanged()
            .flatMapLatest { (query, sort) -> getDiscoverGroupsPagingDataUseCase(query, sort) }
            .cachedIn(viewModelScope)
            .onEach(::setPagingData)
            .launchIn(viewModelScope)
    }

    data class UiState(
        val pagingData: PagingData<DiscoverGroup> = PagingData.empty(),
        val query: String = "",
        val sort: DiscoverSort = DiscoverSort.RECENT,
        // 다이얼로그 버튼 로딩 표시용 — 동시에 하나만 처리
        val joiningGroupId: Long? = null,
        // 가입/신청/취소 직후 서버 재조회 없이 카드·다이얼로그 상태를 낙관적으로 덮어쓴다
        val localOverrides: Map<Long, GroupMembershipStatus> = emptyMap(),
        val error: String? = null,
        // 초대코드 다이얼로그 전용 — 목록 에러(error)와 분리해 다이얼로그 안에서만 그린다
        val isJoiningByCode: Boolean = false,
        val joinByCodeError: String? = null
    ) {
        fun membershipOf(group: DiscoverGroup): GroupMembershipStatus = localOverrides[group.id] ?: group.membership
    }

    sealed interface Action {
        data class Search(val query: String) : Action
        data class ChangeSort(val sort: DiscoverSort) : Action
        data class Join(val groupId: Long) : Action
        data class JoinByCode(val code: String) : Action
        data object DismissJoinByCodeError : Action
        data class CancelRequest(val groupId: Long) : Action
    }

    sealed interface Event {
        /** 자동 승인으로 즉시 가입 완료 — 화면이 세션 GroupsViewModel을 갱신한다 */
        data object Joined : Event

        /** 초대코드로 가입 완료 — 화면이 다이얼로그를 닫고 세션 GroupsViewModel을 갱신한다 */
        data object JoinedByCode : Event

        /** 승인제 신청/신청 취소 — 화면이 세션 GroupsViewModel의 가입 신청중 섹션만 갱신한다 */
        data object MembershipChanged : Event
    }
}
