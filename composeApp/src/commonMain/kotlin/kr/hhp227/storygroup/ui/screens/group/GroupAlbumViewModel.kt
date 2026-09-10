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
import kr.hhp227.storygroup.shared.domain.model.GroupPhoto
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupPhotosPagingDataUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel

/**
 * 앨범 탭 — 레거시 AlbumFragment의 VM 미러(탭별 VM 분리). 게시글 첨부의 파생 뷰라
 * 페이징 스트림 하나가 전부다. 갱신은 화면이 Event를 받아 프레젠터 refresh()로 수행한다
 * — 뷰가 직접 refresh()를 부르면 복귀 직후 프레젠터가 아직 첫 PagingData를 받기 전이라 호출이
 * 유실된다(피드 탭과 동일 규약). iosApp GroupAlbumViewModel.swift와 1:1 미러
 */
class GroupAlbumViewModel(
    val groupId: Long,
    getGroupPhotosPagingDataUseCase: GetGroupPhotosPagingDataUseCase
) : ViewModel(), MviViewModel<GroupAlbumViewModel.UiState, GroupAlbumViewModel.Action, GroupAlbumViewModel.Event> {
    private val _uiState = MutableStateFlow(UiState())
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<Event>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val event: Flow<Event> = _event.asSharedFlow()

    override fun onAction(action: Action) {
        when (action) {
            // 글쓰기(사진 첨부) 성공·당겨서 새로고침 시 발화 — 화면이 refresh()로 첫 페이지부터 다시 읽는다
            Action.Refresh -> _event.tryEmit(Event.Refresh)
        }
    }

    init {
        // UseCase는 cachedIn 없는 Flow를 반환하므로 프레젠테이션 경계인 여기서 캐시를 적용한다
        getGroupPhotosPagingDataUseCase(groupId)
            .cachedIn(viewModelScope)
            .onEach { pagingData -> _uiState.update { it.copy(photosPagingData = pagingData) } }
            .launchIn(viewModelScope)
    }

    data class UiState(
        val photosPagingData: PagingData<GroupPhoto> = PagingData.empty()
    )

    sealed interface Action {
        data object Refresh : Action
    }

    sealed interface Event {
        data object Refresh : Event
    }
}
