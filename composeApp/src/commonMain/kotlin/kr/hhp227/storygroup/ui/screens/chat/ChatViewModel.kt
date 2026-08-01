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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.shared.domain.model.DirectRoom
import kr.hhp227.storygroup.shared.domain.model.GroupChatRoom
import kr.hhp227.storygroup.shared.domain.model.PersonalEvent
import kr.hhp227.storygroup.shared.domain.model.PersonalEventType
import kr.hhp227.storygroup.shared.domain.usecase.GetDirectRoomsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupChatRoomsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.ObservePersonalEventsUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel

/**
 * 채팅 허브 — 웹 /dm 미러: 그룹 채팅방(GET /api/chat-rooms, 라운지 제외)+DM 방(GET /api/dm)
 * 두 목록을 병렬 조회한다. 방별 미읽음 수는 서버 집계(unreadCount)를 스냅숏으로 받고,
 * 개인 큐(STOMP) CHAT_MESSAGE 이벤트로 실시간 증가시킨다 — 셸 채팅 탭 뱃지(totalUnread)도 이 값의 합.
 * 열려 있는 방(RoomOpened~RoomClosed)의 이벤트는 방 화면이 직접 표시·읽음 보고하므로 올리지 않는다.
 * iosApp ChatViewModel.swift와 1:1 미러
 */
class ChatViewModel(
    private val getGroupChatRoomsUseCase: GetGroupChatRoomsUseCase,
    private val getDirectRoomsUseCase: GetDirectRoomsUseCase,
    observePersonalEventsUseCase: ObservePersonalEventsUseCase
) : ViewModel(), MviViewModel<ChatViewModel.UiState, ChatViewModel.Action, Nothing> {
    private val _uiState = MutableStateFlow(UiState())
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    override val event: Flow<Nothing> = emptyFlow()

    // 지금 열려 있는 채팅방 — 그 방의 CHAT_MESSAGE는 뱃지 대상이 아니다
    private var activeRoomId: Long? = null

    // 재연결부터만 재조회를 걸기 위한 가드 — 첫 연결은 init의 초기 로드와 겹친다(채팅방 VM 미러)
    private var hasConnectedOnce = false

    override fun onAction(action: Action) {
        when (action) {
            Action.Refresh -> load()
            is Action.RoomOpened -> onRoomOpened(action.chatRoomId)
            is Action.RoomClosed -> onRoomClosed(action.chatRoomId)
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

    /** 방 진입 — 방 화면이 최신 메시지를 읽고 자동 보고하므로 미읽음을 낙관적으로 0 처리한다 */
    private fun onRoomOpened(chatRoomId: Long) {
        activeRoomId = chatRoomId
        setUnreadCount(chatRoomId) { 0 }
    }

    private fun onRoomClosed(chatRoomId: Long) {
        if (activeRoomId == chatRoomId) activeRoomId = null
    }

    /** 개인 큐 실시간 이벤트 — 목록에 있는 방이면 미읽음 +1, 모르는 방(새 DM 등)이면 목록 재조회 */
    private fun handlePersonalEvent(personalEvent: PersonalEvent) {
        when (personalEvent.type) {
            PersonalEventType.CONNECTED -> {
                // 재연결이면 끊김 공백에 놓친 이벤트를 서버 집계 재조회로 메꾼다
                if (hasConnectedOnce) load()
                hasConnectedOnce = true
            }
            PersonalEventType.CHAT_MESSAGE -> {
                val roomId = personalEvent.chatRoomId ?: return
                if (roomId == activeRoomId) return
                val state = _uiState.value
                if (state.groupRooms.any { it.id == roomId } || state.directRooms.any { it.id == roomId }) {
                    setUnreadCount(roomId) { it + 1 }
                } else {
                    load()
                }
            }
            // NOTIFICATION은 알림 VM 소관, DISCONNECTED는 재연결 CONNECTED가 정리한다
            else -> Unit
        }
    }

    private fun setUnreadCount(chatRoomId: Long, transform: (Long) -> Long) {
        _uiState.update { state ->
            state.copy(
                groupRooms = state.groupRooms.map {
                    if (it.id == chatRoomId) it.copy(unreadCount = transform(it.unreadCount)) else it
                },
                directRooms = state.directRooms.map {
                    if (it.id == chatRoomId) it.copy(unreadCount = transform(it.unreadCount)) else it
                }
            )
        }
    }

    init {
        load()
        observePersonalEventsUseCase()
            .onEach(::handlePersonalEvent)
            .launchIn(viewModelScope)
    }

    data class UiState(
        val groupRooms: List<GroupChatRoom> = emptyList(),
        val directRooms: List<DirectRoom> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null
    ) {
        /** 셸 채팅 탭 뱃지 — 모든 방 미읽음의 합 */
        val totalUnread: Long
            get() = groupRooms.sumOf { it.unreadCount } + directRooms.sumOf { it.unreadCount }
    }

    sealed interface Action {
        data object Refresh : Action

        /** 채팅방 화면 진입/이탈 신호 — 진입한 방은 미읽음 0 처리 후 실시간 증가에서 제외한다 */
        data class RoomOpened(val chatRoomId: Long) : Action
        data class RoomClosed(val chatRoomId: Long) : Action
    }
}
