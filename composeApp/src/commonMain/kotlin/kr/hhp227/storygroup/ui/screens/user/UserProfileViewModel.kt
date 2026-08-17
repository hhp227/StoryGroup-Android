package kr.hhp227.storygroup.ui.screens.user

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
import kr.hhp227.storygroup.shared.domain.model.PublicProfile
import kr.hhp227.storygroup.shared.domain.usecase.AddFriendUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetCurrentUserIdUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetFriendsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetPublicProfileUseCase
import kr.hhp227.storygroup.shared.domain.usecase.OpenDirectRoomUseCase
import kr.hhp227.storygroup.shared.domain.usecase.RemoveFriendUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel

/**
 * 공개 프로필 — 웹 /users/[userId] 미러. 본인이면 액션이 "프로필 수정"뿐이고,
 * 타인이면 1:1 DM+친구 추가/해제. 친구 여부는 응답에 없어 친구 목록과 대조하며,
 * 목록 로드 실패 시 isFriend=null 유지 → 화면이 친구 버튼을 숨긴다(웹 isFriend===null 미러 —
 * 해제가 오동작하면 안 되므로 검색 화면의 "기본 친구 추가"와 달리 숨김이 맞다).
 * iosApp UserProfileViewModel.swift와 1:1 미러
 */
class UserProfileViewModel(
    private val userId: Long,
    private val getPublicProfileUseCase: GetPublicProfileUseCase,
    private val getFriendsUseCase: GetFriendsUseCase,
    private val addFriendUseCase: AddFriendUseCase,
    private val removeFriendUseCase: RemoveFriendUseCase,
    private val openDirectRoomUseCase: OpenDirectRoomUseCase,
    getCurrentUserIdUseCase: GetCurrentUserIdUseCase
) : ViewModel(), MviViewModel<UserProfileViewModel.UiState, UserProfileViewModel.Action, UserProfileViewModel.Event> {
    private val _uiState = MutableStateFlow(UiState(isSelf = getCurrentUserIdUseCase() == userId))
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<Event>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val event: Flow<Event> = _event.asSharedFlow()

    override fun onAction(action: Action) {
        when (action) {
            Action.Refresh -> load()
            Action.ToggleFriend -> toggleFriend()
            Action.OpenDm -> openDm()
            Action.DismissActionError -> _uiState.update { it.copy(actionError = null) }
        }
    }

    private fun load() {
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            runCatching { getPublicProfileUseCase(userId) }
                .onSuccess { profile ->
                    _uiState.update { it.copy(isLoading = false, profile = profile) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, loadError = e.message ?: "프로필을 불러오지 못했습니다.") }
                }
        }
    }

    /** 친구 버튼 상태용 — 실패는 조용히 무시하고 isFriend=null 유지(버튼 숨김, 웹 미러) */
    private fun loadFriendState() {
        if (_uiState.value.isSelf) return

        viewModelScope.launch {
            runCatching { getFriendsUseCase() }
                .onSuccess { friends ->
                    _uiState.update { it.copy(isFriend = friends.any { friend -> friend.userId == userId }) }
                }
        }
    }

    /** 친구 토글 — 성공 시 isFriend만 뒤집는다(웹 setIsFriend(!isFriend) 미러). 409 등은 서버 문구 */
    private fun toggleFriend() {
        val isFriend = _uiState.value.isFriend ?: return

        if (_uiState.value.isBusy) return

        _uiState.update { it.copy(isBusy = true, actionError = null) }
        viewModelScope.launch {
            runCatching { if (isFriend) removeFriendUseCase(userId) else addFriendUseCase(userId) }
                .onSuccess {
                    _uiState.update { it.copy(isBusy = false, isFriend = !isFriend) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isBusy = false, actionError = e.message ?: "친구 처리에 실패했습니다.") }
                }
        }
    }

    /** 1:1 DM — get-or-create(멱등). 방 이름은 서버가 "DM" 고정이라 상대 이름을 제목으로 넘긴다(친구 탭 미러) */
    private fun openDm() {
        val profile = _uiState.value.profile ?: return

        if (_uiState.value.isOpeningDm) return

        _uiState.update { it.copy(isOpeningDm = true, actionError = null) }
        viewModelScope.launch {
            runCatching { openDirectRoomUseCase(userId) }
                .onSuccess { chatRoomId ->
                    _uiState.update { it.copy(isOpeningDm = false) }
                    _event.tryEmit(Event.DmOpened(chatRoomId, profile.name))
                }
                .onFailure { e ->
                    // 차단 관계(403 BLOCKED) 등 — 인라인 문구로 노출
                    _uiState.update { it.copy(isOpeningDm = false, actionError = e.message ?: "DM을 시작하지 못했습니다.") }
                }
        }
    }

    init {
        load()
        loadFriendState()
    }

    data class UiState(
        val profile: PublicProfile? = null,
        val isLoading: Boolean = false,
        val loadError: String? = null,
        // null=친구 여부 판정 불가(목록 로드 실패) → 화면이 친구 버튼을 숨긴다(웹 미러)
        val isFriend: Boolean? = null,
        // 친구 토글 진행 중 — 버튼 비활성(웹 isBusy 미러)
        val isBusy: Boolean = false,
        val isOpeningDm: Boolean = false,
        val actionError: String? = null,
        // 본인 여부(JWT sub 대조) — 본인이면 액션이 "프로필 수정"뿐(웹 isSelf 미러)
        val isSelf: Boolean = false
    )

    sealed interface Action {
        data object Refresh : Action
        data object ToggleFriend : Action
        data object OpenDm : Action
        data object DismissActionError : Action
    }

    sealed interface Event {
        /** DM 방 확보 성공 — 화면이 채팅방(groupId=null)으로 이동한다 */
        data class DmOpened(val chatRoomId: Long, val title: String) : Event
    }
}
