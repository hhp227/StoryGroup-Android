package kr.hhp227.storygroup.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.shared.domain.model.SearchResults
import kr.hhp227.storygroup.shared.domain.model.UserSearchResult
import kr.hhp227.storygroup.shared.domain.usecase.AddFriendUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetFriendsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.RemoveFriendUseCase
import kr.hhp227.storygroup.shared.domain.usecase.SearchUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel

/**
 * 홈 통합검색 — 웹 /search 미러(제출 기반, 5섹션 원페이지).
 * results가 null이면 검색 전(빈 상태 안내) — 친구 탭 searchResults 상태 축 미러.
 * users 섹션의 친구 여부는 응답에 없어 init에서 친구 목록을 읽어 friendIds로 판정(웹 미러).
 * iosApp SearchViewModel.swift와 1:1 미러
 */
class SearchViewModel(
    private val searchUseCase: SearchUseCase,
    private val getFriendsUseCase: GetFriendsUseCase,
    private val addFriendUseCase: AddFriendUseCase,
    private val removeFriendUseCase: RemoveFriendUseCase
) : ViewModel(), MviViewModel<SearchViewModel.UiState, SearchViewModel.Action, Nothing> {
    private val _uiState = MutableStateFlow(UiState())
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // 일회성 이벤트 없음 — 결과 탭 내비게이션은 화면 콜백 몫
    override val event: Flow<Nothing> = emptyFlow()

    override fun onAction(action: Action) {
        when (action) {
            is Action.Search -> search(action.query)
            Action.ClearResults -> _uiState.update { it.copy(results = null, error = null) }
            is Action.AddFriend -> addFriend(action.user)
            is Action.RemoveFriend -> removeFriend(action.userId)
            Action.DismissActionError -> _uiState.update { it.copy(actionError = null) }
        }
    }

    /** 통합검색(제출 기반) — 빈 검색어는 초기 화면 복귀(친구 탭 미러, 서버 400 선차단) */
    private fun search(query: String) {
        val trimmed = query.trim()

        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(results = null, error = null) }
            return
        }
        if (_uiState.value.isSearching) return

        _uiState.update { it.copy(isSearching = true, error = null) }
        viewModelScope.launch {
            runCatching { searchUseCase(trimmed) }
                .onSuccess { results ->
                    _uiState.update { it.copy(isSearching = false, results = results) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isSearching = false, error = e.message ?: "검색에 실패했습니다.") }
                }
        }
    }

    /** users 섹션 버튼 판정용 친구 목록 — 실패해도 검색은 동작해야 하므로 조용히 무시(버튼은 "친구 추가" 기본, 중복 등록은 409 문구가 흡수) */
    private fun loadFriendIds() {
        viewModelScope.launch {
            runCatching { getFriendsUseCase() }
                .onSuccess { friends ->
                    _uiState.update { it.copy(friendIds = friends.map { friend -> friend.userId }.toSet()) }
                }
        }
    }

    /** 친구 등록 — friendIds만 낙관적 갱신(친구 탭과 달리 이 화면엔 친구 목록이 없다). 409는 서버 문구 노출 */
    private fun addFriend(user: UserSearchResult) {
        if (_uiState.value.processingUserId != null) return

        _uiState.update { it.copy(processingUserId = user.id, actionError = null) }
        viewModelScope.launch {
            runCatching { addFriendUseCase(user.id) }
                .onSuccess {
                    _uiState.update { it.copy(processingUserId = null, friendIds = it.friendIds + user.id) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(processingUserId = null, actionError = e.message ?: "친구 등록에 실패했습니다.")
                    }
                }
        }
    }

    private fun removeFriend(userId: Long) {
        if (_uiState.value.processingUserId != null) return

        _uiState.update { it.copy(processingUserId = userId, actionError = null) }
        viewModelScope.launch {
            runCatching { removeFriendUseCase(userId) }
                .onSuccess {
                    _uiState.update { it.copy(processingUserId = null, friendIds = it.friendIds - userId) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(processingUserId = null, actionError = e.message ?: "친구 해제에 실패했습니다.")
                    }
                }
        }
    }

    init {
        loadFriendIds()
    }

    data class UiState(
        // null=검색 전(빈 상태 안내) — 웹 /search results 상태 축 미러
        val results: SearchResults? = null,
        val isSearching: Boolean = false,
        // 검색 실패 문구 — 직전 결과를 대체하지 않고 위에 얹는다(친구 탭 미러)
        val error: String? = null,
        // users 섹션 "친구 추가/해제" 토글 판정 — init 로드+낙관적 갱신
        val friendIds: Set<Long> = emptySet(),
        // 추가/해제 버튼 로딩 표시 — 동시에 하나만 처리(친구 탭 미러)
        val processingUserId: Long? = null,
        val actionError: String? = null
    )

    sealed interface Action {
        data class Search(val query: String) : Action
        data object ClearResults : Action
        data class AddFriend(val user: UserSearchResult) : Action
        data class RemoveFriend(val userId: Long) : Action
        data object DismissActionError : Action
    }
}
