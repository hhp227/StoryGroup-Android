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
import kr.hhp227.storygroup.shared.domain.model.Group
import kr.hhp227.storygroup.shared.domain.usecase.GetMyGroupsPagingDataUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel

/**
 * 그룹 탭 목록 — 레거시 user_groups 페이징 미러(라운지 제외는 shared 데이터 계층).
 * VM은 캐시(cachedIn)와 갱신 Event 발화만 담당 — 갱신은 화면이 Event를 받아 프레젠터
 * refresh()로 수행한다(홈 피드와 동일 패턴, 스트림 교체 없음).
 * 세션 스코프에 선언되어 "생성 = 세션 진입 1회"이므로 init에서 바로 시작한다(재로그인 시 재생성).
 * iosApp GroupsViewModel.swift와 1:1 미러
 */
class GroupsViewModel(
    getMyGroupsPagingDataUseCase: GetMyGroupsPagingDataUseCase
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
            Action.Refresh -> _event.tryEmit(Event.Refresh)
        }
    }

    init {
        // UseCase는 cachedIn 없는 Flow를 반환하므로 프레젠테이션 경계인 여기서 캐시를 적용한다
        getMyGroupsPagingDataUseCase()
            .cachedIn(viewModelScope)
            .onEach(::setPagingData)
            .launchIn(viewModelScope)
    }

    /** 그룹 목록은 Paging 스트림의 최신 스냅샷 — 로딩/에러/추가 로드는 화면이 LoadState로 그린다 */
    data class UiState(val pagingData: PagingData<Group> = PagingData.empty())

    sealed interface Action {
        data object Refresh : Action
    }

    sealed interface Event {
        data object Refresh : Event
    }
}
