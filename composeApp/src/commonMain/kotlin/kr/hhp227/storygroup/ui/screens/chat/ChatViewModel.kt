package kr.hhp227.storygroup.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.shared.domain.model.DirectRoom
import kr.hhp227.storygroup.shared.domain.model.GroupChatRoom
import kr.hhp227.storygroup.shared.domain.usecase.GetDirectRoomsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupChatRoomsUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel

/**
 * 채팅 허브 — 웹 /dm 미러: 그룹 채팅방(GET /api/chat-rooms, 라운지 제외)+DM 방(GET /api/dm)
 * 두 목록을 병렬 조회한다. 서버 응답엔 마지막 메시지/미읽음 수가 없어 웹처럼 이름만 그린다.
 * iosApp ChatViewModel.swift와 1:1 미러
 */
class ChatViewModel(
    private val getGroupChatRoomsUseCase: GetGroupChatRoomsUseCase,
    private val getDirectRoomsUseCase: GetDirectRoomsUseCase
) : ViewModel(), MviViewModel<ChatViewModel.UiState, ChatViewModel.Action, Nothing> {
    private val _uiState = MutableStateFlow(UiState())
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    override val event: Flow<Nothing> = emptyFlow()

    override fun onAction(action: Action) {
        when (action) {
            Action.Refresh -> load()
        }
    }

    private fun load() {
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                coroutineScope {
                    val groupRooms = async { getGroupChatRoomsUseCase() }
                    val directRooms = async { getDirectRoomsUseCase() }
                    groupRooms.await() to directRooms.await()
                }
            }
                .onSuccess { (groupRooms, directRooms) ->
                    _uiState.update {
                        it.copy(isLoading = false, groupRooms = groupRooms, directRooms = directRooms)
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "채팅방을 불러오지 못했습니다.") }
                }
        }
    }

    init {
        load()
    }

    data class UiState(
        val groupRooms: List<GroupChatRoom> = emptyList(),
        val directRooms: List<DirectRoom> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null
    )

    sealed interface Action {
        data object Refresh : Action
    }
}
