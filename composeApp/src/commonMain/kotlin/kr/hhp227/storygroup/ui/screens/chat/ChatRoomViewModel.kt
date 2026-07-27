package kr.hhp227.storygroup.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kr.hhp227.storygroup.shared.domain.model.ChatEvent
import kr.hhp227.storygroup.shared.domain.model.ChatEventType
import kr.hhp227.storygroup.shared.domain.model.ChatMessage
import kr.hhp227.storygroup.shared.domain.usecase.GetChatMessagesUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetChatReadPositionsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetCurrentUserIdUseCase
import kr.hhp227.storygroup.shared.domain.usecase.MarkChatMessagesReadUseCase
import kr.hhp227.storygroup.shared.domain.usecase.ObserveChatRoomEventsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.SendChatMessageUseCase
import kr.hhp227.storygroup.shared.domain.usecase.SendChatTypingUseCase
import kr.hhp227.storygroup.shared.domain.usecase.UploadChatFileUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel

/**
 * 채팅방 — 이력은 REST 최신순 오프셋 페이징(웹은 최신 50 고정, 앱은 "이전 메시지" 추가 로드),
 * 실시간 수신은 STOMP 구독(VM 수명 = 소켓 수명), 전송·읽음 보고는 REST(웹 미러).
 * 메시지 목록은 서버 응답 그대로 최신순으로 들고, 화면이 뒤집어 위=과거·아래=최신으로 그린다.
 * 재연결(CONNECTED)·연결 유실(DISCONNECTED) 시 최신 페이지를 다시 읽어 끊김 공백을 메꾼다 —
 * 유실 시 REST 재조회는 만료 토큰도 리프레시해 다음 재연결 시도를 살리는 역할을 겸한다.
 * 첨부는 전송 시점 업로드(웹 미러 — 고르기만 하고 안 보내면 스토리지에 고아가 안 남는다),
 * 타이핑은 스로틀 발신+수신 자동 소멸, "읽음 N"은 멤버별 읽음 위치에서 파생한다(전부 웹 미러).
 * iosApp ChatRoomViewModel.swift와 1:1 미러
 */
class ChatRoomViewModel(
    private val groupId: Long?,
    private val chatRoomId: Long,
    private val getChatMessagesUseCase: GetChatMessagesUseCase,
    private val sendChatMessageUseCase: SendChatMessageUseCase,
    private val markChatMessagesReadUseCase: MarkChatMessagesReadUseCase,
    private val uploadChatFileUseCase: UploadChatFileUseCase,
    private val sendChatTypingUseCase: SendChatTypingUseCase,
    private val getChatReadPositionsUseCase: GetChatReadPositionsUseCase,
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

    // 타이핑 발신 스로틀 기준점 — 첫 키 입력은 즉시 나간다(리딩 에지, 웹 미러)
    private var lastTypingSentAt: TimeSource.Monotonic.ValueTimeMark? = null

    // 타이핑 수신자별 자동 소멸 타이머 — 같은 사람의 신호가 오면 리셋된다
    private val typingExpiryJobs = mutableMapOf<Long, Job>()

    override fun onAction(action: Action) {
        when (action) {
            Action.Refresh -> loadLatest()
            Action.LoadOlder -> loadOlder()
            is Action.Send -> send(action.text)
            is Action.Attach -> _uiState.update {
                it.copy(
                    pendingAttachment = PendingAttachment(action.bytes, action.fileName, action.contentType),
                    actionError = null
                )
            }
            Action.ClearAttachment -> _uiState.update { it.copy(pendingAttachment = null) }
            Action.Typing -> sendTypingThrottled()
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
                    // 읽음 위치도 함께 새로 고침 — 재연결 시 끊김 동안의 READ 이벤트 공백을 메꾼다
                    loadReadPositions()
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
        val pending = _uiState.value.pendingAttachment
        // 첨부가 있으면 본문 없이도 보낼 수 있다(웹 미러 — 서버는 둘 다 비었을 때만 400)
        if ((trimmed.isEmpty() && pending == null) || _uiState.value.isSending) return

        _uiState.update { it.copy(isSending = true, actionError = null) }
        viewModelScope.launch {
            runCatching {
                val attachment = pending?.let { uploadChatFileUseCase(it.bytes, it.fileName, it.contentType) }

                sendChatMessageUseCase(groupId, chatRoomId, trimmed, attachment)
            }
                .onSuccess { message ->
                    _uiState.update { it.copy(isSending = false, pendingAttachment = null) }
                    // STOMP 브로드캐스트가 먼저 도착했을 수 있어 id 중복 제거를 거친다(웹 미러)
                    appendMessage(message)
                    _event.tryEmit(Event.Sent)
                }
                .onFailure { e ->
                    // 첨부는 유지 — 업로드/전송 실패 시 같은 첨부로 재시도할 수 있다(웹 미러)
                    _uiState.update { it.copy(isSending = false, actionError = e.message ?: "전송에 실패했습니다.") }
                }
        }
    }

    /** 타이핑 신호 스로틀 발신 — 2.5초에 한 번, 발신 실패·미연결은 조용히 버려진다(웹 미러) */
    private fun sendTypingThrottled() {
        val mark = lastTypingSentAt
        if (mark != null && mark.elapsedNow() < TYPING_SEND_INTERVAL) return

        lastTypingSentAt = TimeSource.Monotonic.markNow()
        viewModelScope.launch { sendChatTypingUseCase(chatRoomId) }
    }

    /** 타이핑 수신 — 4초 무신호면 지운다. 발신 간격(2.5초) < 소멸(4초)이라 깜빡이지 않는다(웹 미러) */
    private fun noteTypist(userId: Long, userName: String) {
        _uiState.update { it.copy(typists = it.typists + (userId to userName)) }
        typingExpiryJobs.remove(userId)?.cancel()
        typingExpiryJobs[userId] = viewModelScope.launch {
            delay(TYPING_HIDE)
            typingExpiryJobs.remove(userId)
            _uiState.update { it.copy(typists = it.typists - userId) }
        }
    }

    private fun clearTypist(userId: Long) {
        typingExpiryJobs.remove(userId)?.cancel()
        _uiState.update { if (userId in it.typists) it.copy(typists = it.typists - userId) else it }
    }

    private fun handleEvent(event: ChatEvent) {
        when (event.type) {
            ChatEventType.CONNECTED ->
                if (hasConnectedOnce) loadLatest() else hasConnectedOnce = true
            ChatEventType.DISCONNECTED -> loadLatest()
            ChatEventType.MESSAGE_CREATED -> event.message?.let { message ->
                // 메시지가 도착했으면 그 사람의 "입력 중"은 소멸 타이머를 기다리지 않고 즉시 걷는다(웹 미러)
                clearTypist(message.userId)
                appendMessage(message)
            }
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
            ChatEventType.TYPING -> {
                val userId = event.userId
                // 서버는 발신자 본인에게도 릴레이한다 — 내 타이핑은 거른다(웹 미러)
                if (userId != null && userId != _uiState.value.myUserId) {
                    noteTypist(userId, event.userName.orEmpty())
                }
            }
            ChatEventType.READ -> {
                val userId = event.userId
                // READ의 messageId는 "그 사람의 마지막 읽음 위치" — 순서 보장이 없어 max 병합(웹 미러)
                val lastReadMessageId = event.messageId
                if (userId != null && lastReadMessageId != null) {
                    _uiState.update { state ->
                        val current = state.readPositions[userId] ?: 0L

                        if (lastReadMessageId > current) {
                            state.copy(readPositions = state.readPositions + (userId to lastReadMessageId))
                        } else state
                    }
                }
            }
            // 프레즌스("보고 있어요") 표시는 후속
            ChatEventType.PRESENCE -> Unit
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

    /** 멤버별 읽음 위치 스냅숏 — 실패해도 치명적이지 않아 조용히 넘어간다(이후 READ 이벤트가 채운다) */
    private fun loadReadPositions() {
        viewModelScope.launch {
            runCatching { getChatReadPositionsUseCase(groupId, chatRoomId) }
                .onSuccess { positions ->
                    _uiState.update { state ->
                        // 조회 중 도착한 READ 이벤트가 응답보다 새것일 수 있어 max 병합
                        val merged = state.readPositions.toMutableMap()

                        positions.forEach { position ->
                            merged[position.userId] =
                                maxOf(merged[position.userId] ?: 0L, position.lastReadMessageId)
                        }
                        state.copy(readPositions = merged)
                    }
                }
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
        // 전송 대기 첨부(메시지당 1개, 전송 시점 업로드) — 실패해도 유지돼 재시도할 수 있다
        val pendingAttachment: PendingAttachment? = null,
        // 입력 중인 타인(userId→이름) — 신호가 끊기면 4초 뒤 자동 소멸
        val typists: Map<Long, String> = emptyMap(),
        // 멤버별 마지막 읽음 위치(userId→messageId, 본인 포함) — "읽음 N"은 화면이 파생한다
        val readPositions: Map<Long, Long> = emptyMap(),
        // 이력 로드 에러 — 목록이 비었을 때만 화면을 대체한다
        val error: String? = null,
        // 전송/이전 로드 실패 문구 — 목록을 대체하지 않는다(가입 신청 인박스 actionError 패턴)
        val actionError: String? = null
    )

    /** 전송 대기 첨부 — ByteArray라 data class equals가 무의미해 일반 클래스로 둔다 */
    class PendingAttachment(
        val bytes: ByteArray,
        val fileName: String,
        val contentType: String
    ) {
        val isImage: Boolean get() = contentType.startsWith("image/")
    }

    sealed interface Action {
        data object Refresh : Action
        data object LoadOlder : Action
        data class Send(val text: String) : Action
        /** 피커 선택 결과 — 업로드는 전송 시점까지 미룬다 */
        class Attach(val bytes: ByteArray, val fileName: String, val contentType: String) : Action
        data object ClearAttachment : Action
        /** 입력 변화 신호 — VM이 스로틀해 STOMP 타이핑 신호로 발신한다 */
        data object Typing : Action
    }

    sealed interface Event {
        /** 전송 성공 — 화면이 입력을 비우고 맨 아래로 스크롤한다 */
        data object Sent : Event
    }

    companion object {
        // 서버 상한과 동일(웹도 50 고정) — 한 번에 최대한 넓은 공백 메꿈
        const val PAGE_SIZE = 50

        // 웹 TYPING_SEND_INTERVAL_MS/TYPING_HIDE_MS 미러 — 발신 간격 < 소멸 시간이라 연속 입력 중 깜빡이지 않는다
        private val TYPING_SEND_INTERVAL = 2500.milliseconds
        private val TYPING_HIDE = 4000.milliseconds
    }
}
