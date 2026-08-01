package kr.hhp227.storygroup.ui.screens.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.paging.PagingData
import app.cash.paging.cachedIn
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
import kr.hhp227.storygroup.shared.domain.model.AppNotification
import kr.hhp227.storygroup.shared.domain.model.PersonalEvent
import kr.hhp227.storygroup.shared.domain.model.PersonalEventType
import kr.hhp227.storygroup.shared.domain.usecase.GetNotificationsPagingDataUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetUnreadNotificationCountUseCase
import kr.hhp227.storygroup.shared.domain.usecase.MarkAllNotificationsAsReadUseCase
import kr.hhp227.storygroup.shared.domain.usecase.MarkNotificationAsReadUseCase
import kr.hhp227.storygroup.shared.domain.usecase.ObservePersonalEventsUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel

/**
 * 알림 — 웹 /notifications 미러. 목록은 UiState에 담기는 최신 PagingData(다른 페이징 화면과 동일),
 * 미읽음 수는 unread-count API로 별도 로드(첫 페이지 밖 미읽음까지 반영 — "모두 읽음 처리" 노출 기준).
 * 세션 스코프 VM이라 진입마다 Action.Refresh가 미읽음 수를 다시 읽고 Event.RefreshList로 목록도
 * 첫 페이지부터 다시 읽는다(새 알림 확인이 이 화면의 목적이라 스크롤 보존보다 신선도가 우선).
 * 단건 읽음은 서버 재조회 없이 readOverrides로 낙관 갱신한다(그룹 탐색 localOverrides 패턴).
 * 셸 종 아이콘 뱃지도 이 VM의 unreadCount를 공유한다 — 세션 시작에 즉시 로드하고,
 * 개인 큐(STOMP) NOTIFICATION 이벤트로 실시간 증가시킨다.
 * iosApp NotificationsViewModel.swift와 1:1 미러
 */
class NotificationsViewModel(
    getNotificationsPagingDataUseCase: GetNotificationsPagingDataUseCase,
    private val getUnreadNotificationCountUseCase: GetUnreadNotificationCountUseCase,
    private val markNotificationAsReadUseCase: MarkNotificationAsReadUseCase,
    private val markAllNotificationsAsReadUseCase: MarkAllNotificationsAsReadUseCase,
    observePersonalEventsUseCase: ObservePersonalEventsUseCase
) : ViewModel(), MviViewModel<NotificationsViewModel.UiState, NotificationsViewModel.Action, NotificationsViewModel.Event> {
    private val _uiState = MutableStateFlow(UiState())
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<Event>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val event: Flow<Event> = _event.asSharedFlow()

    // 재연결부터만 refresh를 걸기 위한 가드 — 첫 연결은 init의 초기 로드와 겹친다(채팅방 VM 미러)
    private var hasConnectedOnce = false

    private fun setPagingData(pagingData: PagingData<AppNotification>) {
        _uiState.update { it.copy(pagingData = pagingData) }
    }

    override fun onAction(action: Action) {
        when (action) {
            Action.Refresh -> refresh()
            is Action.MarkAsRead -> markAsRead(action.notificationId)
            Action.MarkAllAsRead -> markAllAsRead()
        }
    }

    /** 진입 시 발화 — 미읽음 수 재조회+목록 새로고침(목록 자체는 Pager가 로드/재시도) */
    private fun refresh() {
        _event.tryEmit(Event.RefreshList)
        viewModelScope.launch {
            // 미읽음 수는 보조 정보 — 실패해도 목록은 그려지므로 이전 값을 유지하고 조용히 넘어간다
            runCatching { getUnreadNotificationCountUseCase() }
                .onSuccess { count -> _uiState.update { it.copy(unreadCount = count) } }
        }
    }

    /** 단건 읽음 — 성공 시 목록 재조회 없이 해당 항목만 낙관 갱신한다(스크롤 보존) */
    private fun markAsRead(notificationId: Long) {
        if (_uiState.value.processingId != null) return

        _uiState.update { it.copy(processingId = notificationId, actionError = null) }
        viewModelScope.launch {
            runCatching { markNotificationAsReadUseCase(notificationId) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            processingId = null,
                            readOverrides = it.readOverrides + notificationId,
                            unreadCount = (it.unreadCount - 1).coerceAtLeast(0)
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(processingId = null, actionError = e.message ?: "읽음 처리에 실패했습니다.") }
                }
        }
    }

    /** 전체 읽음 — 성공 시 목록을 첫 페이지부터 다시 읽어 서버 상태를 그대로 반영한다(웹 미러) */
    private fun markAllAsRead() {
        if (_uiState.value.isMarkingAll) return

        _uiState.update { it.copy(isMarkingAll = true, actionError = null) }
        viewModelScope.launch {
            runCatching { markAllNotificationsAsReadUseCase() }
                .onSuccess {
                    _uiState.update {
                        it.copy(isMarkingAll = false, unreadCount = 0, readOverrides = emptySet())
                    }
                    _event.tryEmit(Event.RefreshList)
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isMarkingAll = false, actionError = e.message ?: "전체 읽음 처리에 실패했습니다.") }
                }
        }
    }

    /** 개인 큐 실시간 이벤트 — NOTIFICATION이면 뱃지 수를 올리고, 열려 있는 알림 화면 목록도 갱신시킨다 */
    private fun handlePersonalEvent(personalEvent: PersonalEvent) {
        when (personalEvent.type) {
            PersonalEventType.CONNECTED -> {
                // 재연결이면 끊김 공백에 놓친 알림 수를 REST로 메꾼다
                if (hasConnectedOnce) refresh()
                hasConnectedOnce = true
            }
            PersonalEventType.NOTIFICATION -> {
                _uiState.update { it.copy(unreadCount = it.unreadCount + 1) }
                _event.tryEmit(Event.RefreshList)
            }
            // CHAT_MESSAGE는 채팅 허브 VM 소관, DISCONNECTED는 재연결 CONNECTED가 정리한다
            else -> Unit
        }
    }

    init {
        // UseCase는 cachedIn 없는 Flow를 반환하므로 프레젠테이션 경계인 여기서 캐시를 적용한다
        getNotificationsPagingDataUseCase()
            .cachedIn(viewModelScope)
            .onEach(::setPagingData)
            .launchIn(viewModelScope)
        // 셸 종 뱃지가 세션 시작부터 그려지므로 화면 진입을 기다리지 않고 미읽음 수를 로드한다
        refresh()
        observePersonalEventsUseCase()
            .onEach(::handlePersonalEvent)
            .launchIn(viewModelScope)
    }

    data class UiState(
        val pagingData: PagingData<AppNotification> = PagingData.empty(),
        // unread-count API 값 — 첫 페이지 밖 미읽음까지 포함("모두 읽음 처리" 노출 기준)
        val unreadCount: Long = 0,
        // 단건 읽음 직후 서버 재조회 없이 항목 표시만 낙관적으로 덮어쓴다(그룹 탐색 localOverrides 패턴)
        val readOverrides: Set<Long> = emptySet(),
        // 읽음 버튼 로딩 표시용 — 동시에 하나만 처리(가입 신청 인박스와 동일)
        val processingId: Long? = null,
        val isMarkingAll: Boolean = false,
        // 읽음 처리 실패 문구 — 목록 로드 에러(Paging LoadState)와 달리 목록을 대체하지 않는다
        val actionError: String? = null
    ) {
        fun isRead(notification: AppNotification): Boolean =
            notification.isRead || notification.id in readOverrides
    }

    sealed interface Action {
        data object Refresh : Action
        data class MarkAsRead(val notificationId: Long) : Action
        data object MarkAllAsRead : Action
    }

    sealed interface Event {
        /** 목록 새로고침 — 화면이 프레젠터 refresh()로 첫 페이지부터 다시 읽는다(홈 피드와 동일 패턴) */
        data object RefreshList : Event
    }
}
