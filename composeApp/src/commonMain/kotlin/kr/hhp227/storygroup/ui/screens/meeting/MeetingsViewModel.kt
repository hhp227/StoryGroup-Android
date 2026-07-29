package kr.hhp227.storygroup.ui.screens.meeting

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
import kr.hhp227.storygroup.shared.domain.model.Meeting
import kr.hhp227.storygroup.shared.domain.usecase.CreateMeetingUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupMeetingsPagingDataUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel

/**
 * 그룹 화상회의 목록 — 목록은 UiState에 담기는 최신 PagingData(그룹 상세 피드와 동일 패턴).
 * "회의 시작"은 바디 없는 POST — 성공 시 목록을 갱신하고 상세로 바로 진입한다(웹 미러).
 * iosApp MeetingsViewModel.swift와 1:1 미러
 */
class MeetingsViewModel(
    val groupId: Long,
    private val createMeetingUseCase: CreateMeetingUseCase,
    getGroupMeetingsPagingDataUseCase: GetGroupMeetingsPagingDataUseCase
) : ViewModel(), MviViewModel<MeetingsViewModel.UiState, MeetingsViewModel.Action, MeetingsViewModel.Event> {
    private val _uiState = MutableStateFlow(UiState())
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<Event>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val event: Flow<Event> = _event.asSharedFlow()

    private fun setPagingData(pagingData: PagingData<Meeting>) {
        _uiState.update { it.copy(pagingData = pagingData) }
    }

    override fun onAction(action: Action) {
        when (action) {
            Action.CreateMeeting -> createMeeting()
            Action.DismissError -> _uiState.update { it.copy(actionError = null) }
        }
    }

    /** 회의 시작 — 서버가 생성자를 참가자로 자동 등록하므로 성공 시 상세로 바로 들어간다 */
    private fun createMeeting() {
        if (_uiState.value.isCreating) return

        _uiState.update { it.copy(isCreating = true, actionError = null) }
        viewModelScope.launch {
            runCatching { createMeetingUseCase(groupId) }
                .onSuccess { meeting ->
                    _uiState.update { it.copy(isCreating = false) }
                    _event.tryEmit(Event.MeetingCreated(meeting.id))
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isCreating = false, actionError = e.message ?: "회의를 시작하지 못했습니다.") }
                }
        }
    }

    init {
        // UseCase는 cachedIn 없는 Flow를 반환하므로 프레젠테이션 경계인 여기서 캐시를 적용한다
        getGroupMeetingsPagingDataUseCase(groupId)
            .cachedIn(viewModelScope)
            .onEach(::setPagingData)
            .launchIn(viewModelScope)
    }

    data class UiState(
        val pagingData: PagingData<Meeting> = PagingData.empty(),
        val isCreating: Boolean = false,
        // 시작 실패 문구 — 목록 로드 에러는 Paging LoadState가 담당하므로 여긴 액션 에러만
        val actionError: String? = null
    )

    sealed interface Action {
        data object CreateMeeting : Action
        data object DismissError : Action
    }

    sealed interface Event {
        /** 회의 생성 성공 — 화면이 목록을 갱신하고 상세로 이동한다 */
        data class MeetingCreated(val meetingId: Long) : Event
    }
}
