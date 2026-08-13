package kr.hhp227.storygroup.ui.screens.friend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.shared.domain.model.Friend
import kr.hhp227.storygroup.shared.domain.model.UserSearchResult
import kr.hhp227.storygroup.shared.domain.usecase.AddFriendUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetFriendsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.OpenDirectRoomUseCase
import kr.hhp227.storygroup.shared.domain.usecase.RemoveFriendUseCase
import kr.hhp227.storygroup.shared.domain.usecase.SearchUsersUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel

/**
 * 친구 탭 — 웹 /search(친구 목록 기본 화면+검색 결과 토글, 카카오톡 친구 탭 패턴) 미러.
 * searchResults가 null이면 검색 전(친구 목록), 채워지면 검색 결과 — 웹의 results 상태 축 미러.
 * 단, 웹은 한 번 검색하면 친구 목록으로 못 돌아가는데(페이지 재진입 필요) 여기는
 * 검색어를 비우면 즉시 친구 목록으로 복귀한다. 세션 스코프 VM이라 init 로드가 세션당 1회.
 * iosApp FriendsViewModel.swift와 1:1 미러
 */
class FriendsViewModel(
    private val getFriendsUseCase: GetFriendsUseCase,
    private val addFriendUseCase: AddFriendUseCase,
    private val removeFriendUseCase: RemoveFriendUseCase,
    private val searchUsersUseCase: SearchUsersUseCase,
    private val openDirectRoomUseCase: OpenDirectRoomUseCase
) : ViewModel(), MviViewModel<FriendsViewModel.UiState, FriendsViewModel.Action, FriendsViewModel.Event> {
    private val _uiState = MutableStateFlow(UiState())
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<Event>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val event: Flow<Event> = _event.asSharedFlow()

    override fun onAction(action: Action) {
        when (action) {
            Action.Refresh -> refresh()
            is Action.Search -> search(action.query)
            is Action.AddFriend -> addFriend(action.user)
            is Action.RemoveFriend -> removeFriend(action.userId)
            is Action.OpenDm -> openDm(action.userId, action.userName)
            Action.DismissActionError -> _uiState.update { it.copy(actionError = null) }
        }
    }

    /** 친구 목록 로드 — 웹처럼 마운트(세션 진입) 1회 + 에러 재시도(웹 listFriends 미러) */
    private fun refresh() {
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { getFriendsUseCase() }
                .onSuccess { friends ->
                    _uiState.update { it.copy(isLoading = false, friends = friends) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = e.message ?: "친구 목록을 불러오지 못했습니다.")
                    }
                }
        }
    }

    /** 사용자 검색(제출 기반) — 빈 검색어는 친구 목록 복귀(웹과 달리 복귀 가능) */
    private fun search(query: String) {
        val trimmed = query.trim()

        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(searchResults = null, searchError = null) }
            return
        }
        if (_uiState.value.isSearching) return

        _uiState.update { it.copy(isSearching = true, searchError = null) }
        viewModelScope.launch {
            runCatching { searchUsersUseCase(trimmed) }
                .onSuccess { users ->
                    _uiState.update { it.copy(isSearching = false, searchResults = users) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isSearching = false, searchError = e.message ?: "검색에 실패했습니다.")
                    }
                }
        }
    }

    /** 친구 등록 — 재조회 없이 검색 결과를 친구로 변환해 이름순 위치에 끼워 넣는다(웹 낙관적 갱신 미러) */
    private fun addFriend(user: UserSearchResult) {
        if (_uiState.value.processingUserId != null) return

        _uiState.update { it.copy(processingUserId = user.id, actionError = null) }
        viewModelScope.launch {
            runCatching { addFriendUseCase(user.id) }
                .onSuccess {
                    _uiState.update {
                        val added = Friend(
                            userId = user.id,
                            name = user.name,
                            profileImg = user.profileImg,
                            statusMessage = user.statusMessage
                        )

                        it.copy(
                            processingUserId = null,
                            friends = (it.friends + added).sortedBy { friend -> friend.name }
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(processingUserId = null, actionError = e.message ?: "친구 등록에 실패했습니다.")
                    }
                }
        }
    }

    /** 친구 해제 — 성공 시 목록에서만 제거(웹 removeFriend 미러). 검색 결과 토글도 friendIds로 따라온다 */
    private fun removeFriend(userId: Long) {
        if (_uiState.value.processingUserId != null) return

        _uiState.update { it.copy(processingUserId = userId, actionError = null) }
        viewModelScope.launch {
            runCatching { removeFriendUseCase(userId) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            processingUserId = null,
                            friends = it.friends.filterNot { friend -> friend.userId == userId }
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(processingUserId = null, actionError = e.message ?: "친구 해제에 실패했습니다.")
                    }
                }
        }
    }

    /** 친구와 1:1 DM 열기 — get-or-create(멱등)라 이미 방이 있으면 그 방으로 간다(웹 "메시지" 버튼 미러) */
    private fun openDm(userId: Long, userName: String) {
        if (_uiState.value.isOpeningDm) return

        _uiState.update { it.copy(isOpeningDm = true, actionError = null) }
        viewModelScope.launch {
            runCatching { openDirectRoomUseCase(userId) }
                .onSuccess { chatRoomId ->
                    _uiState.update { it.copy(isOpeningDm = false) }
                    // 방 이름은 서버가 "DM" 고정이라 상대 이름을 제목으로 넘긴다(허브와 동일)
                    _event.tryEmit(Event.DmOpened(chatRoomId, userName))
                }
                .onFailure { e ->
                    // 차단 관계(403 BLOCKED) 등 — 목록 상단에 표시된다
                    _uiState.update { it.copy(isOpeningDm = false, actionError = e.message ?: "DM을 열지 못했습니다.") }
                }
        }
    }

    init {
        refresh()
    }

    data class UiState(
        // 서버가 이름순으로 정렬해 준다 — 낙관적 갱신도 이름순 위치를 유지한다
        val friends: List<Friend> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null,
        // null=검색 전(친구 목록 표시) — 웹 /search의 results 상태 축 미러
        val searchResults: List<UserSearchResult>? = null,
        val isSearching: Boolean = false,
        // 검색 실패 문구 — 직전 표시(친구 목록/이전 결과)를 대체하지 않고 위에 얹는다
        val searchError: String? = null,
        // 추가/해제 버튼 로딩 표시용 — 동시에 하나만 처리(웹 busy 미러)
        val processingUserId: Long? = null,
        val isOpeningDm: Boolean = false,
        // 추가/해제/DM 실패 문구 — 로드 에러(error)와 달리 목록 화면을 대체하지 않는다
        val actionError: String? = null
    ) {
        /** 검색 결과의 "친구 추가/해제" 토글 판정 — 응답에 친구 여부가 없어 친구 목록과 대조한다(웹 friendIds 미러) */
        val friendIds: Set<Long> get() = friends.map { it.userId }.toSet()
    }

    sealed interface Action {
        data object Refresh : Action
        data class Search(val query: String) : Action
        data class AddFriend(val user: UserSearchResult) : Action
        data class RemoveFriend(val userId: Long) : Action
        data class OpenDm(val userId: Long, val userName: String) : Action
        data object DismissActionError : Action
    }

    sealed interface Event {
        /** DM 방 확보 성공 — 화면이 채팅방(groupId=null)으로 이동한다 */
        data class DmOpened(val chatRoomId: Long, val title: String) : Event
    }
}
