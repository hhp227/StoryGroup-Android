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
import kr.hhp227.storygroup.shared.domain.model.GroupPhoto
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupPhotosPagingDataUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel

/**
 * 앨범 탭 — 레거시 AlbumFragment의 VM 미러(탭별 VM 분리). 게시글 첨부의 파생 뷰라
 * 페이징 스트림 하나가 전부다. 갱신은 화면이 프레젠터 refresh()로 수행하고 사용자
 * 액션도 없다 — ACTION/EVENT 둘 다 Nothing(호출 불가). iosApp GroupAlbumViewModel.swift와 1:1 미러
 */
class GroupAlbumViewModel(
    val groupId: Long,
    getGroupPhotosPagingDataUseCase: GetGroupPhotosPagingDataUseCase
) : ViewModel(), MviViewModel<GroupAlbumViewModel.UiState, Nothing, Nothing> {
    private val _uiState = MutableStateFlow(UiState())
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    override val event: Flow<Nothing> = emptyFlow()

    override fun onAction(action: Nothing) = Unit

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
}
