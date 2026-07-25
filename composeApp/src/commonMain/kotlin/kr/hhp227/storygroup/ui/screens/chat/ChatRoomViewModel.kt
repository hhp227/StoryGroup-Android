package kr.hhp227.storygroup.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.shared.domain.model.ChatEvent
import kr.hhp227.storygroup.shared.domain.model.ChatEventType
import kr.hhp227.storygroup.shared.domain.model.ChatMessage
import kr.hhp227.storygroup.shared.domain.usecase.GetChatMessagesUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetCurrentUserIdUseCase
import kr.hhp227.storygroup.shared.domain.usecase.MarkChatMessagesReadUseCase
import kr.hhp227.storygroup.shared.domain.usecase.ObserveChatRoomEventsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.SendChatMessageUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel

/**
 * 채팅방 — 이력은 REST 최신순 오프셋 페이징(웹은 최신 50 고정, 앱은 "이전 메시지" 추가 로드),
 * 실시간 수신은 STOMP 구독(VM 수명 = 소켓 수명), 전송·읽음 보고는 REST(웹 미러).
 * 메시지 목록은 서버 응답 그대로 최신순으로 들고, 화면이 뒤집어 위=과거·아래=최신으로 그린다.
 * 재연결(CONNECTED)·연결 유실(DISCONNECTED) 시 최신 페이지를 다시 읽어 끊김 공백을 메꾼다 —
 * 유실 시 REST 재조회는 만료 토큰도 리프레시해 다음 재연결 시도를 살리는 역할을 겸한다.
 * iosApp ChatRoomViewModel.swift와 1:1 미러
 */
class ChatRoomViewModel(
    private val groupId: Long?,
    private val chatRoomId: Long,
    private val getChatMessagesUseCase: GetChatMessagesUseCase,
    private val sendChatMessageUseCase: SendChatMessageUseCase,
    private val markChatMessagesReadUseCase: MarkChatMessagesReadUseCase,
    observeChatRoomEventsUseCase: ObserveChatRoomEventsUseCase,
    getCurrentUserIdUseCase: GetCurrentUserIdUseCase
) : ViewModel(), MviViewModel<ChatRoomViewModel.UiState, ChatRoomViewModel.Action, ChatRoomViewModel.Event> {
    private val _uiState = MutableStateFlow(UiState(myUserId = getCurrentUserIdUseCase()))
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<Event>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val event: Flow<Event> = _event.asSharedFlow()

    // 최신순 페이징 커서 — 재조회(loadLatest)마다 0으로 되돌아간다(웹 전체 교체 미러)
    private var oldestLoadedPage = 0

    // 첫 CONNECTED는 init 로드와 겹치므로 재연결부터 공백 메꿈 재조회를 한다
    private var hasConnectedOnce = false

    override fun onAction(action: Action) {
        when (action) {
            Action.Refresh -> loadLatest()
            Action.LoadOlder -> loadOlder()
            is Action.Send -> send(action.text)
        }
    }

    /** 최신 페이지로 전체 교체 — 진입/재시도/재연결 공용(웹 refresh-on-reconnect 미러) */
    private fun loadLatest() {
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { getChatMessagesUseCase(groupId, chatRoomId, page = 0, size = PAGE_SIZE) }
                .onSuccess { fetched ->
                    oldestLoadedPage = 0
                    _uiState.update {
                        it.copy(isLoading = false, messages = fetched, canLoadOlder = fetched.size == PAGE_SIZE)
                    }
                    // 최신순 첫 항목 = 가장 최근 메시지
                    fetched.firstOrNull()?.let { reportRead(it.id) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "메시지를 불러오지 못했습니다.") }
                }
        }
    }

    private fun loadOlder() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingOlder || !state.canLoadOlder) return

        _uiState.update { it.copy(isLoadingOlder = true, actionError = null) }
        viewModelScope.launch {
            val page = oldestLoadedPage + 1

            runCatching { getChatMessagesUseCase(groupId, chatRoomId, page, PAGE_SIZE) }
                .onSuccess { fetched ->
                    oldestLoadedPage = page
                    _uiState.update { current ->
                        val knownIds = current.messages.map { it.id }.toSet()

                        current.copy(
                            isLoadingOlder = false,
                            // 로드 사이 새 메시지 유입으로 오프셋이 밀리면 중복이 올 수 있어 id로 거른다
                            messages = current.messages + fetched.filter { it.id !in knownIds },
                            canLoadOlder = fetched.size == PAGE_SIZE
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoadingOlder = false, actionError = e.message ?: "이전 메시지를 불러오지 못했습니다.") }
                }
        }
    }

    private fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _uiState.value.isSending) return

        _uiState.update { it.copy(isSending = true, actionError = null) }
        viewModelScope.launch {
            runCatching { sendChatMessageUseCase(groupId, chatRoomId, trimmed) }
                .onSuccess { message ->
                    _uiState.update { it.copy(isSending = false) }
                    // STOMP 브로드캐스트가 먼저 도착했을 수 있어 id 중복 제거를 거친다(웹 미러)
                    appendMessage(message)
                    _event.tryEmit(Event.Sent)
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isSending = false, actionError = e.message ?: "전송에 실패했습니다.") }
                }
        }
    }

    private fun handleEvent(event: ChatEvent) {
        when (event.type) {
            ChatEventType.CONNECTED ->
                if (hasConnectedOnce) loadLatest() else hasConnectedOnce = true
            ChatEventType.DISCONNECTED -> loadLatest()
            ChatEventType.MESSAGE_CREATED -> event.message?.let(::appendMessage)
            ChatEventType.MESSAGE_UPDATED -> event.message?.let { updated ->
                _uiState.update { state ->
                    state.copy(messages = state.messages.map { if (it.id == updated.id) updated else it })
                }
            }
            ChatEventType.MESSAGE_DELETED -> event.messageId?.let { deletedId ->
                _uiState.update { state ->
                    state.copy(messages = state.messages.filterNot { it.id == deletedId })
                }
            }
            // 타이핑/프레즌스/읽음 수 표시는 후속
            else -> Unit
        }
    }

    private fun appendMessage(message: ChatMessage) {
        if (_uiState.value.messages.any { it.id == message.id }) return

        _uiState.update { it.copy(messages = listOf(message) + it.messages) }
        reportRead(message.id)
    }

    /** 읽음 보고는 파이어-앤-포겟 — 실패해도 다음 보고가 따라잡는다(서버 GREATEST 단조 보장) */
    private fun reportRead(lastReadMessageId: Long) {
        viewModelScope.launch {
            runCatching { markChatMessagesReadUseCase(groupId, chatRoomId, lastReadMessageId) }
        }
    }

    init {
        loadLatest()
        // 구독 수명 = VM 수명 — 화면을 떠나면(백스택 pop) 소켓도 함께 닫힌다
        observeChatRoomEventsUseCase(chatRoomId)
            .onEach(::handleEvent)
            .launchIn(viewModelScope)
    }

    data class UiState(
        // 말풍선 내/타인 정렬 기준(JWT sub) — 세션이 있는 한 null이 아니다
        val myUserId: Long? = null,
        // 서버 응답 그대로 최신순 — 화면이 뒤집어 그린다(첫 항목 = 맨 아래 최신)
        val messages: List<ChatMessage> = emptyList(),
        val isLoading: Boolean = false,
        val isLoadingOlder: Boolean = false,
        // 마지막으로 읽은 페이지가 꽉 찼으면 더 오래된 메시지가 남아있다고 본다
        val canLoadOlder: Boolean = false,
        val isSending: Boolean = false,
        // 이력 로드 에러 — 목록이 비었을 때만 화면을 대체한다
        val error: String? = null,
        // 전송/이전 로드 실패 문구 — 목록을 대체하지 않는다(가입 신청 인박스 actionError 패턴)
        val actionError: String? = null
    )

    sealed interface Action {
        data object Refresh : Action
        data object LoadOlder : Action
        data class Send(val text: String) : Action
    }

    sealed interface Event {
        /** 전송 성공 — 화면이 입력을 비우고 맨 아래로 스크롤한다 */
        data object Sent : Event
    }

    companion object {
        // 서버 상한과 동일(웹도 50 고정) — 한 번에 최대한 넓은 공백 메꿈
        const val PAGE_SIZE = 50
    }
}
