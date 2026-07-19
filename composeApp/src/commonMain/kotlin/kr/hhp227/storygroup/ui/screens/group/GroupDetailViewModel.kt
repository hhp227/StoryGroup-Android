package kr.hhp227.storygroup.ui.screens.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.shared.domain.model.Group
import kr.hhp227.storygroup.shared.domain.model.GroupMember
import kr.hhp227.storygroup.shared.domain.model.Post
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupMembersUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupPostsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel

/**
 * 그룹 상세 — 웹 /groups/[id] 미러(커버+피드+멤버). 목록에서 받은 그룹으로 즉시 그리고,
 * Refresh에서 단건 조회로 신선화한다. iosApp GroupDetailViewModel.swift와 1:1 미러
 */
class GroupDetailViewModel(
    initialGroup: Group,
    private val getGroupUseCase: GetGroupUseCase,
    private val getGroupMembersUseCase: GetGroupMembersUseCase,
    private val getGroupPostsUseCase: GetGroupPostsUseCase
) : ViewModel(), MviViewModel<GroupDetailViewModel.UiState, GroupDetailViewModel.Action, Nothing> {
    private val _uiState = MutableStateFlow(UiState(group = initialGroup))
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    override val event: Flow<Nothing> = emptyFlow()

    private var nextPage = 0

    override fun onAction(action: Action) {
        when (action) {
            Action.Refresh -> refresh()
            Action.LoadMore -> loadMore()
        }
    }

    /** 상세 진입 시 발화 — 그룹 신선화+멤버+첫 페이지(이전 내용은 로딩 중에도 유지) */
    private fun refresh() {
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val groupId = _uiState.value.group.id

            runCatching {
                val group = getGroupUseCase(groupId)
                val members = getGroupMembersUseCase(groupId)
                val posts = getGroupPostsUseCase(groupId, 0, PAGE_SIZE)
                Triple(group, members, posts)
            }.onSuccess { (group, members, posts) ->
                nextPage = 1
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        group = group,
                        members = members,
                        posts = posts,
                        hasMore = posts.size == PAGE_SIZE
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "그룹을 불러오지 못했습니다.")
                }
            }
        }
    }

    /** 목록 끝 도달 시 발화 — 다음 페이지를 이어 붙인다(HomeViewModel 미러) */
    private fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return

        _uiState.update { it.copy(isLoadingMore = true, error = null) }
        viewModelScope.launch {
            runCatching { getGroupPostsUseCase(_uiState.value.group.id, nextPage, PAGE_SIZE) }
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
        val group: Group,
        val members: List<GroupMember> = emptyList(),
        val posts: List<Post> = emptyList(),
        val isLoading: Boolean = false,
        val isLoadingMore: Boolean = false,
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
