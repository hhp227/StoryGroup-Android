package kr.hhp227.storygroup.ui.screens.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.paging.PagingData
import app.cash.paging.cachedIn
import app.cash.paging.filter
import app.cash.paging.map
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.shared.domain.model.Post
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupPostsPagingDataUseCase
import kr.hhp227.storygroup.shared.domain.usecase.ObservePostDeletionsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.ObservePostUpdatesUseCase
import kr.hhp227.storygroup.shared.domain.usecase.ObserveUserBlocksUseCase
import kr.hhp227.storygroup.shared.domain.usecase.TogglePostLikeUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel
import org.jetbrains.compose.resources.getString
import storygroup.composeapp.generated.resources.Res
import storygroup.composeapp.generated.resources.post_error_like

/**
 * 소식 탭 — 레거시 PostFragment의 VM 미러(탭별 VM 분리). 피드는 UiState에 담기는 최신
 * PagingData(Paging-CRUD 샘플 패턴). 갱신은 화면이 프레젠터 refresh()로 수행하므로
 * 이벤트가 없다. 수정/차단/삭제는 재조회 대신 현재 스냅샷에서 그 항목만 패치한다.
 * iosApp GroupFeedViewModel.swift와 1:1 미러
 */
class GroupFeedViewModel(
    val groupId: Long,
    getGroupPostsPagingDataUseCase: GetGroupPostsPagingDataUseCase,
    observePostUpdatesUseCase: ObservePostUpdatesUseCase,
    observeUserBlocksUseCase: ObserveUserBlocksUseCase,
    observePostDeletionsUseCase: ObservePostDeletionsUseCase,
    private val togglePostLikeUseCase: TogglePostLikeUseCase
) : ViewModel(), MviViewModel<GroupFeedViewModel.UiState, GroupFeedViewModel.Action, Nothing> {
    private val _uiState = MutableStateFlow(UiState())
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    override val event: Flow<Nothing> = emptyFlow()

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

    /** 차단한 작성자의 글을 현재 스냅샷에서 걷어낸다(멤버 목록은 멤버 탭 Refresh가 걸러낸다) */
    private fun removeBlockedAuthorPosts(userId: Long) {
        _uiState.update { state ->
            state.copy(pagingData = state.pagingData.filter { it.userId != userId })
        }
    }

    /** 삭제된 글을 현재 스냅샷에서 걷어낸다 — 다음 세대부턴 서버 응답에 애초에 없다 */
    private fun removeDeletedPost(postId: Long) {
        _uiState.update { state ->
            state.copy(pagingData = state.pagingData.filter { it.id != postId })
        }
    }

    override fun onAction(action: Action) {
        when (action) {
            is Action.ToggleLike -> toggleLike(action.post)
            Action.DismissLikeError -> _uiState.update { it.copy(likeError = null) }
        }
    }

    /** 성공 반영은 리포지토리의 postUpdates 알림(applyPostUpdate)이 담당 — 여기선 실패만 다룬다 */
    private fun toggleLike(post: Post) {
        viewModelScope.launch {
            try {
                togglePostLikeUseCase(post.groupId, post.id, !post.likedByMe)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(likeError = e.message ?: getString(Res.string.post_error_like)) }
            }
        }
    }

    init {
        // UseCase는 cachedIn 없는 Flow를 반환하므로 프레젠테이션 경계인 여기서 캐시를 적용한다
        getGroupPostsPagingDataUseCase(groupId)
            .cachedIn(viewModelScope)
            .onEach(::setPagingData)
            .launchIn(viewModelScope)
        // 상세 화면에서 수정하면 목록도 바뀐 본문을 보여야 한다 — 재조회 대신 그 항목만 교체
        observePostUpdatesUseCase()
            .onEach(::applyPostUpdate)
            .launchIn(viewModelScope)
        // 차단하면 그 사람의 글이 목록에서 사라져야 한다 — 재조회 대신 그 항목들만 제거
        observeUserBlocksUseCase()
            .onEach(::removeBlockedAuthorPosts)
            .launchIn(viewModelScope)
        // 상세에서 삭제하면 목록에서도 사라져야 한다 — 재조회 대신 그 항목만 제거
        observePostDeletionsUseCase()
            .onEach(::removeDeletedPost)
            .launchIn(viewModelScope)
    }

    data class UiState(
        val pagingData: PagingData<Post> = PagingData.empty(),
        // 카드 좋아요 실패 안내 — 서버 확정 방식이라 실패해도 되돌릴 UI 상태가 없다
        val likeError: String? = null
    )

    sealed interface Action {
        data class ToggleLike(val post: Post) : Action
        data object DismissLikeError : Action
    }
}
