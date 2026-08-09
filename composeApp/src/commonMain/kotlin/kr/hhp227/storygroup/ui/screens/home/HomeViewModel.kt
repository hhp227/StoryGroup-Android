package kr.hhp227.storygroup.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.paging.PagingData
import app.cash.paging.cachedIn
import app.cash.paging.map
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
import kr.hhp227.storygroup.shared.domain.model.Post
import kr.hhp227.storygroup.shared.domain.usecase.GetLoungePostsPagingDataUseCase
import kr.hhp227.storygroup.shared.domain.usecase.ObservePostUpdatesUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel

/**
 * 홈(라운지) 피드 — 페이징(라운지 해석 포함)은 shared 데이터 계층 소유, VM은 캐시(cachedIn)와
 * 갱신 Event 발화만 담당하고 UiState에 최신 PagingData를 담는다(Paging-CRUD 샘플 패턴).
 * 갱신은 화면이 Event를 받아 프레젠터 refresh()로 수행 — 같은 스트림이 새 세대를 방출하므로
 * 스트림 교체(트리거)가 없다. 세션 스코프에 선언되어 "생성 = 세션 진입 1회"이므로 init에서
 * 바로 시작한다(재로그인 시 재생성). iosApp HomeViewModel.swift와 1:1 미러
 */
class HomeViewModel(
    getLoungePostsPagingDataUseCase: GetLoungePostsPagingDataUseCase,
    observePostUpdatesUseCase: ObservePostUpdatesUseCase
) : ViewModel(), MviViewModel<HomeViewModel.UiState, HomeViewModel.Action, HomeViewModel.Event> {
    private val _uiState = MutableStateFlow(UiState())
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<Event>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val event: Flow<Event> = _event.asSharedFlow()

    private fun setPagingData(pagingData: PagingData<Post>) {
        _uiState.update { it.copy(pagingData = pagingData) }
    }

    /**
     * 수정된 게시글을 현재 스냅샷에서 그 항목만 갈아끼운다 — refresh를 태우면 첫 페이지부터
     * 전체 재조회라 이미 쌓아둔 페이지와 스크롤 위치를 잃는다(수정은 목록 구조를 바꾸지 않는다).
     * 다음 세대(새로고침·재진입)부턴 서버 값이 그대로 이긴다.
     */
    private fun applyPostUpdate(post: Post) {
        _uiState.update { state ->
            state.copy(pagingData = state.pagingData.map { if (it.id == post.id) post else it })
        }
    }

    override fun onAction(action: Action) {
        when (action) {
            // 글쓰기 성공 시 발화 — 화면이 refresh()로 라운지를 다시 찾고 첫 페이지부터 다시 읽는다
            Action.Refresh -> _event.tryEmit(Event.Refresh)
        }
    }

    init {
        // UseCase는 cachedIn 없는 Flow를 반환하므로 프레젠테이션 경계인 여기서 캐시를 적용한다
        getLoungePostsPagingDataUseCase()
            .cachedIn(viewModelScope)
            .onEach(::setPagingData)
            .launchIn(viewModelScope)
        // 상세 화면에서 수정하면 목록도 바뀐 본문을 보여야 한다 — 재조회 대신 그 항목만 교체
        observePostUpdatesUseCase()
            .onEach(::applyPostUpdate)
            .launchIn(viewModelScope)
    }

    /** 게시글 목록은 Paging 스트림의 최신 스냅샷 — 로딩/에러/추가 로드는 화면이 LoadState로 그린다 */
    data class UiState(val pagingData: PagingData<Post> = PagingData.empty())

    sealed interface Action {
        data object Refresh : Action
    }

    sealed interface Event {
        data object Refresh : Event
    }
}
