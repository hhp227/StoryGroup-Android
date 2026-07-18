package kr.hhp227.storygroup.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.shared.domain.model.Post
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupPostsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetMyGroupsUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel

/**
 * 홈(라운지) 피드 — 웹 메인 피드와 동일하게 내 그룹에서 라운지를 찾아 그 그룹의 게시글을
 * 페이지 단위로 읽는다. iosApp HomeViewModel.swift와 1:1 미러
 */
class HomeViewModel(
    private val getMyGroupsUseCase: GetMyGroupsUseCase,
    private val getGroupPostsUseCase: GetGroupPostsUseCase
) : ViewModel(), MviViewModel<HomeViewModel.UiState, HomeViewModel.Action, Nothing> {
    private val _uiState = MutableStateFlow(UiState())
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    override val event: Flow<Nothing> = emptyFlow()

    private var loungeId: Long? = null
    private var nextPage = 0

    override fun onAction(action: Action) {
        when (action) {
            Action.Refresh -> refresh()
            Action.LoadMore -> loadMore()
        }
    }

    /** 로그인 세션 진입 시 발화 — 라운지를 다시 찾고 첫 페이지부터 다시 읽는다(이전 목록은 로딩 중에도 유지) */
    private fun refresh() {
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val lounge = getMyGroupsUseCase().firstOrNull { it.isLounge }
                    ?: error("라운지를 찾을 수 없습니다.")
                loungeId = lounge.id
                getGroupPostsUseCase(lounge.id, 0, PAGE_SIZE)
            }.onSuccess { posts ->
                nextPage = 1
                _uiState.update {
                    it.copy(isLoading = false, posts = posts, hasMore = posts.size == PAGE_SIZE)
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "피드를 불러오지 못했습니다.")
                }
            }
        }
    }

    /** 목록 끝 도달 시 발화 — 다음 페이지를 이어 붙인다 */
    private fun loadMore() {
        val groupId = loungeId ?: return
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return

        _uiState.update { it.copy(isLoadingMore = true, error = null) }
        viewModelScope.launch {
            runCatching { getGroupPostsUseCase(groupId, nextPage, PAGE_SIZE) }
                .onSuccess { rows ->
                    nextPage++
                    _uiState.update { current ->
                        // 새 글이 끼어들어 페이지 경계가 밀려도 중복 카드가 생기지 않게 id로 거른다(웹 미러)
                        val seen = current.posts.mapTo(mutableSetOf(), Post::id)
                        current.copy(
                            isLoadingMore = false,
                            posts = current.posts + rows.filter { it.id !in seen },
                            hasMore = rows.size == PAGE_SIZE
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoadingMore = false, error = e.message ?: "피드를 더 불러오지 못했습니다.")
                    }
                }
        }
    }

    data class UiState(
        val isLoading: Boolean = false,
        val isLoadingMore: Boolean = false,
        val posts: List<Post> = emptyList(),
        // 첫 로딩 전에는 false — 푸터(다음 페이지 트리거)가 미리 돌지 않게
        val hasMore: Boolean = false,
        val error: String? = null
    )

    sealed interface Action {
        data object Refresh : Action
        data object LoadMore : Action
    }

    companion object {
        private const val PAGE_SIZE = 20
    }
}
