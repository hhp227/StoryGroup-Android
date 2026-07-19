package kr.hhp227.storygroup.ui.screens.home

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
import kr.hhp227.storygroup.shared.domain.model.Post
import kr.hhp227.storygroup.shared.domain.usecase.GetLoungePostsPagingDataUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel

/**
 * 홈(라운지) 피드 — 페이징(라운지 해석 포함)은 shared 데이터 계층 소유, VM은 캐시(cachedIn)와
 * 갱신 트리거만 담당하고 UiState에 최신 PagingData를 담는다(Paging-CRUD 샘플 패턴).
 * 세션 스코프에 선언되어 "생성 = 세션 진입 1회"이므로 init에서 바로 시작한다(재로그인 시 재생성).
 * iosApp HomeViewModel.swift와 1:1 미러
 */
class HomeViewModel(
    getLoungePostsPagingDataUseCase: GetLoungePostsPagingDataUseCase
) : ViewModel(), MviViewModel<HomeViewModel.UiState, HomeViewModel.Action, Nothing> {
    private val _uiState = MutableStateFlow(UiState())
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    override val event: Flow<Nothing> = emptyFlow()

    // 스트림을 통째로 갈아끼우는 트리거 — 라운지 재해석은 새 PagingSource가 수행
    private val refreshTrigger = MutableStateFlow(0)

    private fun setPagingData(pagingData: PagingData<Post>) {
        _uiState.update { it.copy(pagingData = pagingData) }
    }

    override fun onAction(action: Action) {
        when (action) {
            // 글쓰기 성공 시 발화 — 라운지를 다시 찾고 첫 페이지부터 다시 읽는다
            Action.Refresh -> refreshTrigger.update { it + 1 }
        }
    }

    init {
        // UseCase는 cachedIn 없는 Flow를 반환하므로 프레젠테이션 경계인 여기서 캐시를 적용한다
        @OptIn(ExperimentalCoroutinesApi::class)
        refreshTrigger
            .flatMapLatest { getLoungePostsPagingDataUseCase() }
            .cachedIn(viewModelScope)
            .onEach(::setPagingData)
            .launchIn(viewModelScope)
    }

    /** 게시글 목록은 Paging 스트림의 최신 스냅샷 — 로딩/에러/추가 로드는 화면이 LoadState로 그린다 */
    data class UiState(val pagingData: PagingData<Post> = PagingData.empty())

    sealed interface Action {
        data object Refresh : Action
    }
}
