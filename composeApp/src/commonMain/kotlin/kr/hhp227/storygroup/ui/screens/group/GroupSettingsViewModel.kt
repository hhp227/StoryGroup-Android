package kr.hhp227.storygroup.ui.screens.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.shared.domain.model.Group
import kr.hhp227.storygroup.shared.domain.model.GroupRole
import kr.hhp227.storygroup.shared.domain.usecase.DeleteGroupUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupUseCase
import kr.hhp227.storygroup.shared.domain.usecase.LeaveGroupUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel

/**
 * 설정 탭 — 레거시 SettingsFragment(item_settings.xml) 미러의 메뉴 리스트(탭별 VM 분리).
 * 진입 시 GetGroup으로 self-load해 역할(OWNER 행 구성)·라운지 분기를 판정한다.
 * 수정 폼은 GroupEditViewModel(풀스크린)로 분리 — 여기엔 삭제/나가기만 남는다.
 * iosApp GroupSettingsViewModel.swift와 1:1 미러
 */
class GroupSettingsViewModel(
    val groupId: Long,
    private val getGroupUseCase: GetGroupUseCase,
    private val deleteGroupUseCase: DeleteGroupUseCase,
    private val leaveGroupUseCase: LeaveGroupUseCase
) : ViewModel(), MviViewModel<GroupSettingsViewModel.UiState, GroupSettingsViewModel.Action, GroupSettingsViewModel.Event> {
    private val _uiState = MutableStateFlow(UiState())
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<Event>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val event: Flow<Event> = _event.asSharedFlow()

    override fun onAction(action: Action) {
        when (action) {
            Action.Refresh -> refresh()
            Action.Delete -> close { deleteGroupUseCase(groupId) }
            Action.Leave -> close { leaveGroupUseCase(groupId) }
            Action.DismissCloseError -> _uiState.update { it.copy(closeError = null) }
        }
    }

    /** 진입(init)·재시도·수정 화면 복귀 시 발화 — 역할·라운지 판정용 그룹을 읽는다 */
    private fun refresh() {
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { getGroupUseCase(groupId) }
                .onSuccess { group ->
                    _uiState.update { it.copy(isLoading = false, group = group) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = e.message ?: "그룹 정보를 불러오지 못했습니다.")
                    }
                }
        }
    }

    /** 삭제/나가기 공용 — 성공하면 이 화면 자체가 닫힌다(Closed → pop+목록 갱신) */
    private fun close(operation: suspend () -> Unit) {
        if (_uiState.value.isClosing) return

        _uiState.update { it.copy(isClosing = true, closeError = null) }
        viewModelScope.launch {
            runCatching { operation() }
                .onSuccess {
                    _uiState.update { it.copy(isClosing = false) }
                    _event.tryEmit(Event.Closed)
                }
                .onFailure { e ->
                    // OWNER 나가기 거부("그룹 삭제를 이용하세요") 등 서버 문구를 그대로 보여준다
                    _uiState.update { it.copy(isClosing = false, closeError = e.message ?: "처리에 실패했습니다.") }
                }
        }
    }

    init {
        refresh()
    }

    data class UiState(
        // 로드 원본 — 역할(OWNER 행 구성)·라운지 분기의 기준
        val group: Group? = null,
        val isLoading: Boolean = false,
        val error: String? = null,
        // 삭제/나가기 진행 — 확인 다이얼로그 표시 여부는 화면 로컬 상태
        val isClosing: Boolean = false,
        val closeError: String? = null
    ) {
        val isOwner: Boolean get() = group?.myRole == GroupRole.OWNER
        val isLounge: Boolean get() = group?.isLounge == true

        /**
         * 신고함 노출 조건 — 웹 커버 "신고함" 버튼의 canModerate(myRole) 미러.
         * 라운지도 게시글 신고가 이 신고함으로 접수되므로 GroupDetailViewModel의
         * canModerate(초대코드·일정용, 라운지 제외)와 달리 라운지를 빼지 않는다.
         */
        val canModerate: Boolean get() = group != null && group.myRole != GroupRole.MEMBER
    }

    sealed interface Action {
        data object Refresh : Action
        data object Delete : Action
        data object Leave : Action
        data object DismissCloseError : Action
    }

    sealed interface Event {
        /** 삭제/나가기 성공 — 화면이 onGroupClosed로 pop+그룹 목록 갱신을 요청한다 */
        data object Closed : Event
    }
}
