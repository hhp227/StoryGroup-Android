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
import kr.hhp227.storygroup.shared.domain.model.Group
import kr.hhp227.storygroup.shared.domain.usecase.GetMyGroupsPagingDataUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel

/**
 * 그룹 탭 목록 — 레거시 user_groups 페이징 미러(라운지 제외는 shared 데이터 계층).
 * 세션 스코프에 선언되어 "생성 = 세션 진입 1회"이므로 init에서 바로 시작한다(재로그인 시 재생성).
 * iosApp GroupsViewModel.swift와 1:1 미러
 */
class GroupsViewModel(
    getMyGroupsPagingDataUseCase: GetMyGroupsPagingDataUseCase
) : ViewModel(), MviViewModel<GroupsViewModel.UiState, GroupsViewModel.Action, Nothing> {
    private val _uiState = MutableStateFlow(UiState())
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    override val event: Flow<Nothing> = emptyFlow()

    // 스트림을 통째로 갈아끼우는 트리거 — 그룹 생성/가입 후 갱신용
    private val refreshTrigger = MutableStateFlow(0)

    private fun setPagingData(pagingData: PagingData<Group>) {
        _uiState.update { it.copy(pagingData = pagingData) }
    }

    override fun onAction(action: Action) {
        when (action) {
            Action.Refresh -> refreshTrigger.update { it + 1 }
        }
    }

    init {
        // UseCase는 cachedIn 없는 Flow를 반환하므로 프레젠테이션 경계인 여기서 캐시를 적용한다
        @OptIn(ExperimentalCoroutinesApi::class)
        refreshTrigger
            .flatMapLatest { getMyGroupsPagingDataUseCase() }
            .cachedIn(viewModelScope)
            .onEach(::setPagingData)
            .launchIn(viewModelScope)
    }

    /** 그룹 목록은 Paging 스트림의 최신 스냅샷 — 로딩/에러/추가 로드는 화면이 LoadState로 그린다 */
    data class UiState(val pagingData: PagingData<Group> = PagingData.empty())

    sealed interface Action {
        data object Refresh : Action
    }
}
