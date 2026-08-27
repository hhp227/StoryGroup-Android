package kr.hhp227.storygroup.ui.screens.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.shared.domain.model.EventAttendee
import kr.hhp227.storygroup.shared.domain.model.GroupEvent
import kr.hhp227.storygroup.shared.domain.model.RsvpStatus
import kr.hhp227.storygroup.shared.domain.usecase.CancelEventRsvpUseCase
import kr.hhp227.storygroup.shared.domain.usecase.CreateEventUseCase
import kr.hhp227.storygroup.shared.domain.usecase.DeleteEventUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetCurrentUserIdUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetEventDetailUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupEventsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.RsvpEventUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel
import kr.hhp227.storygroup.ui.util.dateKeyOf
import kr.hhp227.storygroup.ui.util.isoToLocal
import kr.hhp227.storygroup.ui.util.localToIso
import kr.hhp227.storygroup.ui.util.todayLocal
import org.jetbrains.compose.resources.getString
import storygroup.composeapp.generated.resources.Res
import storygroup.composeapp.generated.resources.events_error_create
import storygroup.composeapp.generated.resources.events_error_delete
import storygroup.composeapp.generated.resources.events_error_end_before_start
import storygroup.composeapp.generated.resources.events_error_load
import storygroup.composeapp.generated.resources.events_error_rsvp

/**
 * 일정 탭 — 웹 /groups/[id]/events 캘린더 페이지 미러(탭별 VM 분리, 레거시 탭 Fragment VM 구조).
 * 월 앵커 기준 [월초, 다음달 초) 범위를 조회하고, 날짜 귀속·선택 키는 기기 로컬 타임존
 * yyyy-MM-dd(웹 ymd 미러). RSVP는 낙관적 갱신 없이 서버가 돌려준 집계 갱신 일정으로 카드를
 * 교체하고, 같은 상태 재탭은 취소다. 생성 폼의 날짜는 캘린더 선택일을 그대로 쓴다(M2에
 * DatePicker가 없음) — 그래서 새 일정은 항상 보고 있는 달에 속하고, 웹의 "다른 달이면 이동"
 * 분기가 필요 없다. iosApp GroupEventsViewModel.swift와 1:1 미러
 */
class GroupEventsViewModel(
    val groupId: Long,
    private val getGroupEventsUseCase: GetGroupEventsUseCase,
    private val getEventDetailUseCase: GetEventDetailUseCase,
    private val createEventUseCase: CreateEventUseCase,
    private val deleteEventUseCase: DeleteEventUseCase,
    private val rsvpEventUseCase: RsvpEventUseCase,
    private val cancelEventRsvpUseCase: CancelEventRsvpUseCase,
    getCurrentUserIdUseCase: GetCurrentUserIdUseCase
) : ViewModel(), MviViewModel<GroupEventsViewModel.UiState, GroupEventsViewModel.Action, Nothing> {
    private val _uiState: MutableStateFlow<UiState>
    override val uiState: StateFlow<UiState>

    override val event: Flow<Nothing> = emptyFlow()

    // 진행 중인 월 조회 Job — 월 이동을 연타해도 이전 요청을 취소해 늦게 도착한 이전 달
    // 응답이 새 달 상태를 덮어쓰지 않게 한다(Finding 1 레이스 픽스)
    private var loadJob: Job? = null

    init {
        val today = todayLocal()

        _uiState = MutableStateFlow(
            UiState(
                myUserId = getCurrentUserIdUseCase(),
                year = today.year,
                month = today.month,
                selectedDay = today.dateKey
            )
        )
        uiState = _uiState.asStateFlow()
        loadMonth()
    }

    override fun onAction(action: Action) {
        when (action) {
            Action.Refresh -> loadMonth()
            is Action.MoveMonth -> moveMonth(action.delta)
            Action.GoToday -> goToday()
            is Action.SelectDay -> _uiState.update { it.copy(selectedDay = action.dateKey) }
            Action.ToggleCreateForm -> _uiState.update {
                it.copy(showCreateForm = !it.showCreateForm, createError = null)
            }
            is Action.CreateEvent -> createEvent(action)
            is Action.DeleteEvent -> deleteEvent(action.eventId)
            is Action.Rsvp -> rsvp(action.event, action.status)
            is Action.ToggleAttendees -> toggleAttendees(action.eventId)
            Action.DismissActionError -> _uiState.update { it.copy(actionError = null) }
        }
    }

    /**
     * 현재 앵커 월 재조회 — [월초, 다음달 초) ISO(웹 loadMonth 미러). 진행 중인 이전 요청은
     * 취소하고 교체한다(Finding 1) — 월 이동을 빠르게 연타해도 늦게 도착한 이전 달 응답이
     * 새 달 상태를 덮어쓰지 못하게. runCatching이 CancellationException까지 삼키므로
     * onFailure에서 명시적으로 rethrow해 취소를 정상 전파한다(프로젝트 규칙)
     */
    private fun loadMonth() {
        loadJob?.cancel()

        _uiState.update { it.copy(isLoading = true, error = null) }
        loadJob = viewModelScope.launch {
            val state = _uiState.value
            val (nextYear, nextMonth) =
                if (state.month == 12) state.year + 1 to 1 else state.year to state.month + 1

            runCatching {
                getGroupEventsUseCase(
                    groupId,
                    localToIso(state.year, state.month, 1, 0, 0),
                    localToIso(nextYear, nextMonth, 1, 0, 0)
                )
            }.onSuccess { events ->
                _uiState.update { it.copy(isLoading = false, events = events) }
            }.onFailure { e ->
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isLoading = false, error = e.message ?: getString(Res.string.events_error_load)) }
            }
        }
    }

    /** 월 이동 — 이동한 달의 1일을 선택해 아래 목록이 이전 달 잔상을 가리키지 않게 한다(웹 미러) */
    private fun moveMonth(delta: Int) {
        val state = _uiState.value
        // -1/+1만 쓰지만 산술은 일반화해 둔다(0~11 순환)
        val zeroBased = state.year * 12 + (state.month - 1) + delta
        val year = zeroBased / 12
        val month = zeroBased % 12 + 1

        _uiState.update {
            it.copy(year = year, month = month, selectedDay = dateKeyOf(year, month, 1), events = null)
        }
        loadMonth()
    }

    /** 오늘로 — 다른 달이면 재조회, 같은 달이면 선택만 이동(웹 goToday 미러) */
    private fun goToday() {
        val today = todayLocal()
        val state = _uiState.value

        if (today.year != state.year || today.month != state.month) {
            _uiState.update {
                it.copy(year = today.year, month = today.month, selectedDay = today.dateKey, events = null)
            }
            loadMonth()
        } else {
            _uiState.update { it.copy(selectedDay = today.dateKey) }
        }
    }

    /** 생성 — 날짜는 캘린더 선택일 고정이라 결과는 항상 현재 달: 목록에 넣고 정렬만 하면 된다 */
    private fun createEvent(action: Action.CreateEvent) {
        if (_uiState.value.isCreating) return

        val state = _uiState.value
        val (year, month, day) = state.selectedDay.split("-").map { it.toInt() }
        val endsAtIso = if (action.endHour != null && action.endMinute != null) {
            // 종료가 시작보다 빠르면 폼 검증에서 걸렀어야 한다 — 방어적으로 한 번 더
            if (action.endHour < action.startHour ||
                (action.endHour == action.startHour && action.endMinute < action.startMinute)
            ) {
                // getString은 suspend — 동기 검증 흐름은 그대로 두고 문구 해석만 코루틴에서
                viewModelScope.launch {
                    _uiState.update { it.copy(createError = getString(Res.string.events_error_end_before_start)) }
                }
                return
            }
            localToIso(year, month, day, action.endHour, action.endMinute)
        } else null

        _uiState.update { it.copy(isCreating = true, createError = null) }
        viewModelScope.launch {
            runCatching {
                createEventUseCase(
                    groupId = groupId,
                    title = action.title,
                    description = action.description.ifBlank { null },
                    location = action.location.ifBlank { null },
                    startsAtIso = localToIso(year, month, day, action.startHour, action.startMinute),
                    endsAtIso = endsAtIso
                )
            }.onSuccess { created ->
                _uiState.update {
                    it.copy(
                        isCreating = false,
                        showCreateForm = false,
                        events = ((it.events ?: emptyList()) + created).sortedBy(GroupEvent::startsAt)
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isCreating = false, createError = e.message ?: getString(Res.string.events_error_create)) }
            }
        }
    }

    /** 삭제(작성자/모더레이터 — 서버 검증) — 성공 시 목록에서 제거(웹 handleDelete 미러) */
    private fun deleteEvent(eventId: Long) {
        if (_uiState.value.busyEventId != null) return

        _uiState.update { it.copy(busyEventId = eventId, actionError = null) }
        viewModelScope.launch {
            runCatching { deleteEventUseCase(groupId, eventId) }
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(busyEventId = null, events = state.events?.filterNot { it.id == eventId })
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(busyEventId = null, actionError = e.message ?: getString(Res.string.events_error_delete)) }
                }
        }
    }

    /** RSVP — 같은 상태 재탭=취소. 서버가 돌려준 집계 갱신 일정으로 교체(웹 handleRsvp 미러) */
    private fun rsvp(event: GroupEvent, status: RsvpStatus) {
        if (_uiState.value.busyEventId != null) return

        _uiState.update { it.copy(busyEventId = event.id, actionError = null) }
        viewModelScope.launch {
            runCatching {
                if (event.myRsvp == status) cancelEventRsvpUseCase(groupId, event.id)
                else rsvpEventUseCase(groupId, event.id, status)
            }.onSuccess { updated ->
                _uiState.update { state ->
                    state.copy(
                        busyEventId = null,
                        events = state.events?.map { if (it.id == updated.id) updated else it },
                        // 명단이 바뀌었으니 펼쳐볼 때 다시 불러온다(웹 setAttendees(null) 미러)
                        attendeesByEvent = state.attendeesByEvent - event.id
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(busyEventId = null, actionError = e.message ?: getString(Res.string.events_error_rsvp)) }
            }
        }
    }

    /** 참석자 펼침 — 캐시 없으면 단건 조회, 실패는 조용히 빈 목록(부가 정보 — 웹 미러) */
    private fun toggleAttendees(eventId: Long) {
        val state = _uiState.value

        if (eventId in state.expandedEventIds) {
            _uiState.update { it.copy(expandedEventIds = it.expandedEventIds - eventId) }
            return
        }
        _uiState.update { it.copy(expandedEventIds = it.expandedEventIds + eventId) }
        if (eventId !in state.attendeesByEvent) {
            viewModelScope.launch {
                val attendees = runCatching { getEventDetailUseCase(groupId, eventId).attendees }
                    .getOrDefault(emptyList())

                _uiState.update { it.copy(attendeesByEvent = it.attendeesByEvent + (eventId to attendees)) }
            }
        }
    }

    data class UiState(
        // 일정 카드의 "작성자 본인" 삭제 판정 — 세션이 있는 한 null이 아니다
        val myUserId: Long? = null,
        // 월 앵커(연/월) — 범위 조회와 캘린더 그리드의 기준
        val year: Int = 0,
        val month: Int = 0,
        // 선택일 키(yyyy-MM-dd, 로컬) — 셀 강조와 아래 목록·생성 폼 날짜의 기준
        val selectedDay: String = "",
        // null=이 달을 아직 못 읽음(로딩/실패) — 빈 목록과 구분한다(웹 events === null 미러)
        val events: List<GroupEvent>? = null,
        val isLoading: Boolean = false,
        val error: String? = null,
        val showCreateForm: Boolean = false,
        val isCreating: Boolean = false,
        val createError: String? = null,
        // 카드 액션(RSVP/삭제) 진행 중 일정 — 동시에 하나만(멤버 탭 processingRequestUserId 선례)
        val busyEventId: Long? = null,
        // RSVP/삭제 실패 문구 — 로드 에러(error)와 달리 캘린더를 대체하지 않는다
        val actionError: String? = null,
        val expandedEventIds: Set<Long> = emptySet(),
        // 일정별 참석자 캐시 — RSVP가 바뀌면 그 일정 키를 비워 다음 펼침에 재조회한다
        val attendeesByEvent: Map<Long, List<EventAttendee>> = emptyMap()
    ) {
        /** 로컬 날짜 키별 일정 — 캘린더 점·선택일 목록의 원천(웹 eventsByDay 미러) */
        val eventsByDay: Map<String, List<GroupEvent>>
            get() = (events ?: emptyList()).groupBy { event ->
                isoToLocal(event.startsAt)?.dateKey ?: event.startsAt.take(10)
            }

        val selectedEvents: List<GroupEvent> get() = eventsByDay[selectedDay] ?: emptyList()
    }

    sealed interface Action {
        data object Refresh : Action
        data class MoveMonth(val delta: Int) : Action
        data object GoToday : Action
        data class SelectDay(val dateKey: String) : Action
        data object ToggleCreateForm : Action
        data class CreateEvent(
            val title: String,
            val location: String,
            val description: String,
            val startHour: Int,
            val startMinute: Int,
            val endHour: Int?,
            val endMinute: Int?
        ) : Action
        data class DeleteEvent(val eventId: Long) : Action
        data class Rsvp(val event: GroupEvent, val status: RsvpStatus) : Action
        data class ToggleAttendees(val eventId: Long) : Action
        data object DismissActionError : Action
    }
}
