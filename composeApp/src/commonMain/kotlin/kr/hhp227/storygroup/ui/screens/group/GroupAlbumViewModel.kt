package kr.hhp227.storygroup.ui.screens.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.paging.PagingData
import app.cash.paging.cachedIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kr.hhp227.storygroup.shared.domain.model.GroupPhoto
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupPhotosPagingDataUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel

/**
 * 앨범 탭 — 레거시 AlbumFragment의 VM 미러(탭별 VM 분리). 게시글 첨부의 파생 뷰라
 * 페이징 스트림 하나가 전부다. 갱신은 VM이 스트림을 통째로 갈아끼우는 방식 — 상세가 NavHost
 * 목적지라 글쓰기 복귀 때 컴포지션이 새로 만들어지는데, 그 첫 프레임엔 일회성 Event도 프레젠터
 * refresh()도 닿지 못한다(피드 탭과 동일 규약). iosApp GroupAlbumViewModel.swift와 1:1 미러
 */
class GroupAlbumViewModel(
    val groupId: Long,
    getGroupPhotosPagingDataUseCase: GetGroupPhotosPagingDataUseCase
) : ViewModel(), MviViewModel<GroupAlbumViewModel.UiState, GroupAlbumViewModel.Action, Nothing> {
    private val _uiState = MutableStateFlow(UiState())
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    override val event: Flow<Nothing> = emptyFlow()

    // 스트림을 통째로 갈아끼우는 트리거 — 복귀 첫 프레임에 유실되지 않도록 이벤트가 아닌 상태다
    private val refreshTrigger = MutableStateFlow(0)

    override fun onAction(action: Action) {
        when (action) {
            // 글쓰기(사진 첨부) 성공·당겨서 새로고침 — 새 PagingSource가 첫 페이지부터 다시 읽는다
            Action.Refresh -> refreshTrigger.update { it + 1 }
        }
    }

    init {
        // UseCase는 cachedIn 없는 Flow를 반환하므로 프레젠테이션 경계인 여기서 캐시를 적용한다
        @OptIn(ExperimentalCoroutinesApi::class)
        refreshTrigger
            .flatMapLatest { getGroupPhotosPagingDataUseCase(groupId) }
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
}
