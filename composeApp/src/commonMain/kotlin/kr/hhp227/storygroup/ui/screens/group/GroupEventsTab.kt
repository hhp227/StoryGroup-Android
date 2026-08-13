package kr.hhp227.storygroup.ui.screens.group

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kr.hhp227.storygroup.shared.domain.model.GroupEvent
import kr.hhp227.storygroup.shared.domain.model.RsvpStatus
import kr.hhp227.storygroup.ui.components.SgAvatar
import kr.hhp227.storygroup.ui.components.SgCard
import kr.hhp227.storygroup.ui.components.SgEmptyState
import kr.hhp227.storygroup.ui.components.SgPrimaryButton
import kr.hhp227.storygroup.ui.components.SgTextField
import kr.hhp227.storygroup.ui.theme.SgTheme
import kr.hhp227.storygroup.ui.util.dateKeyOf
import kr.hhp227.storygroup.ui.util.dayOfWeek
import kr.hhp227.storygroup.ui.util.firstDayOfWeekOfMonth
import kr.hhp227.storygroup.ui.util.isoToLocal
import kr.hhp227.storygroup.ui.util.monthLength
import kr.hhp227.storygroup.ui.util.todayLocal

// 웹 DAY_LABELS 미러 — 일=rust, 토=accent 강조는 셀에서 인덱스로 판정
private val DayLabels = listOf("일", "월", "화", "수", "목", "금", "토")

private val RsvpOptions = listOf(
    RsvpStatus.GOING to "참석",
    RsvpStatus.MAYBE to "미정",
    RsvpStatus.NOT_GOING to "불참"
)

/** "HH:mm" — 이벤트 카드 시각 표기(웹 formatTime 미러, 로컬 타임존) */
private fun formatTime(iso: String): String = isoToLocal(iso)?.let {
    "${it.hour.toString().padStart(2, '0')}:${it.minute.toString().padStart(2, '0')}"
}.orEmpty()

/**
 * 일정 탭 — 웹 /groups/[id]/events 미러: 월 캘린더 카드+인라인 생성 폼+선택일 일정 카드 목록.
 * 생성 폼의 날짜는 캘린더 선택일을 그대로 쓴다(M2에 DatePicker가 없음 — 시각만 HH:MM 입력).
 * iosApp GroupEventsTab.swift와 1:1 미러
 */
@Composable
internal fun GroupEventsTab(
    uiState: GroupEventsViewModel.UiState,
    // 일정 삭제 버튼 노출(작성자 본인 외) — 화면이 상세 VM의 canModerate로 게이트
    canModerate: Boolean,
    onAction: (GroupEventsViewModel.Action) -> Unit,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors
    // eventsByDay는 UiState의 파생 get()이라 접근마다 전체 groupBy를 다시 돈다(Finding 2) —
    // 여기서 한 번만 계산해 캘린더 셀 루프(~31회)·선택일 목록 접근에 재사용한다
    val eventsByDay = remember(uiState.events) { uiState.eventsByDay }
    val selectedEvents = remember(eventsByDay, uiState.selectedDay) { eventsByDay[uiState.selectedDay] ?: emptyList() }
    // 삭제 확인 대상 — 스펙 §5: 삭제는 즉시 발화하지 않고 다이얼로그로 한 번 더 확인한다(웹 confirm() 미러)
    var deleteTargetEventId by rememberSaveable { mutableStateOf<Long?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "calendar") {
            // CalendarCard는 캘린더가 실제 쓰는 필드만 받는다(Finding 3) — RSVP/삭제 등
            // 캘린더와 무관한 uiState 변화에 ~35셀 그리드가 전부 리컴포즈되는 걸 막는다
            CalendarCard(
                year = uiState.year,
                month = uiState.month,
                selectedDay = uiState.selectedDay,
                eventsByDay = eventsByDay,
                showCreateForm = uiState.showCreateForm,
                isRefreshingMonth = uiState.isLoading && uiState.events != null,
                onAction = onAction,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        if (uiState.showCreateForm) {
            item(key = "create-form") {
                CreateEventForm(
                    selectedDay = uiState.selectedDay,
                    isCreating = uiState.isCreating,
                    createError = uiState.createError,
                    onCreate = { title, location, description, startHour, startMinute, endHour, endMinute ->
                        onAction(
                            GroupEventsViewModel.Action.CreateEvent(
                                title, location, description, startHour, startMinute, endHour, endMinute
                            )
                        )
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
        item(key = "selected-day-header") {
            // "M월 d일 (요일)" — 웹 선택일 헤더 미러
            val parts = uiState.selectedDay.split("-").mapNotNull { it.toIntOrNull() }

            if (parts.size == 3) {
                val (year, month, day) = parts

                Text(
                    "${month}월 ${day}일 (${DayLabels[dayOfWeek(year, month, day)]})",
                    style = SgTheme.typography.titleSmall,
                    color = sg.ink,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
        uiState.actionError?.let { message ->
            item(key = "action-error") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillParentMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Text(message, style = SgTheme.typography.bodySmall, color = sg.rust, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onAction(GroupEventsViewModel.Action.DismissActionError) }) {
                        Text("닫기", color = sg.accent)
                    }
                }
            }
        }
        when {
            uiState.events == null && uiState.error != null -> item(key = "events-error") {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillParentMaxWidth().padding(vertical = 24.dp)
                ) {
                    Text(uiState.error, style = SgTheme.typography.bodyMedium, color = sg.rust)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { onAction(GroupEventsViewModel.Action.Refresh) }) {
                        Text("다시 시도", color = sg.accent)
                    }
                }
            }
            uiState.events == null -> item(key = "events-loading") {
                Box(Modifier.fillParentMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = sg.accent)
                }
            }
            selectedEvents.isEmpty() -> item(key = "events-empty") {
                SgEmptyState(
                    title = "이 날짜에는 일정이 없습니다",
                    subtitle = "일정 만들기로 첫 일정을 등록해 보세요.",
                    modifier = Modifier.fillParentMaxWidth().padding(vertical = 24.dp)
                )
            }
            else -> items(selectedEvents.size, key = { selectedEvents[it].id }) { index ->
                val event = selectedEvents[index]

                EventCard(
                    event = event,
                    isMine = event.userId == uiState.myUserId,
                    canModerate = canModerate,
                    isBusy = uiState.busyEventId != null,
                    isExpanded = event.id in uiState.expandedEventIds,
                    attendees = uiState.attendeesByEvent[event.id],
                    onAction = onAction,
                    onRequestDelete = { deleteTargetEventId = event.id },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
    deleteTargetEventId?.let { eventId ->
        AlertDialog(
            onDismissRequest = { deleteTargetEventId = null },
            text = { Text("이 일정을 삭제할까요?") },
            confirmButton = {
                TextButton(onClick = {
                    onAction(GroupEventsViewModel.Action.DeleteEvent(eventId))
                    deleteTargetEventId = null
                }) {
                    Text("삭제", color = sg.rust)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargetEventId = null }) {
                    Text("취소", color = sg.ink)
                }
            }
        )
    }
}

/**
 * 월 이동 헤더+7열 그리드 — 웹 캘린더 카드 미러(오늘=테두리, 선택=accent 배경, 점 최대 3개).
 * 캘린더가 실제 쓰는 필드만 받는다(Finding 3) — uiState 전체를 받으면 RSVP/삭제 등 무관한
 * 필드 변화에도 안정성 비교가 깨져 ~35셀 그리드가 매번 리컴포즈된다
 */
@Composable
private fun CalendarCard(
    year: Int,
    month: Int,
    selectedDay: String,
    eventsByDay: Map<String, List<GroupEvent>>,
    showCreateForm: Boolean,
    isRefreshingMonth: Boolean,
    onAction: (GroupEventsViewModel.Action) -> Unit,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors
    val todayKey = todayLocal().dateKey
    val leadingBlanks = firstDayOfWeekOfMonth(year, month)
    val daysInMonth = monthLength(year, month)

    SgCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onAction(GroupEventsViewModel.Action.MoveMonth(-1)) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "이전 달", tint = sg.inkSoft)
                }
                Text(
                    "${year}년 ${month}월",
                    style = SgTheme.typography.titleSmall,
                    color = sg.ink,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { onAction(GroupEventsViewModel.Action.MoveMonth(1)) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "다음 달", tint = sg.inkSoft)
                }
                TextButton(onClick = { onAction(GroupEventsViewModel.Action.GoToday) }) {
                    Text("오늘", color = sg.accent)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onAction(GroupEventsViewModel.Action.ToggleCreateForm) }) {
                    Text(if (showCreateForm) "닫기" else "일정 만들기", color = sg.accent, fontWeight = FontWeight.Bold)
                }
            }
            Row(Modifier.fillMaxWidth()) {
                DayLabels.forEachIndexed { index, label ->
                    Text(
                        label,
                        style = SgTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when (index) {
                            0 -> sg.rust
                            6 -> sg.accent
                            else -> sg.inkFaint
                        },
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.weight(1f).padding(vertical = 4.dp)
                    )
                }
            }
            // 앞쪽 빈 칸(월 시작 요일)+일자들을 7개씩 끊어 행으로 — LazyColumn 안이라 그리드 대신 수동 행
            val cells: List<Int?> = List(leadingBlanks) { null } + (1..daysInMonth).toList()

            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        if (day == null) {
                            Spacer(Modifier.weight(1f).height(52.dp))
                        } else {
                            val key = dateKeyOf(year, month, day)
                            val isSelected = key == selectedDay
                            val isToday = key == todayKey
                            val dayEvents = eventsByDay[key] ?: emptyList()

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) sg.accent else androidx.compose.ui.graphics.Color.Transparent)
                                    .border(
                                        width = 1.5.dp,
                                        color = if (isToday) sg.accent else androidx.compose.ui.graphics.Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onAction(GroupEventsViewModel.Action.SelectDay(key)) }
                                    .padding(top = 6.dp)
                            ) {
                                Text(
                                    day.toString(),
                                    style = SgTheme.typography.bodySmall,
                                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) sg.onAccent else sg.ink
                                )
                                if (dayEvents.isNotEmpty()) {
                                    Spacer(Modifier.height(2.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        dayEvents.take(3).forEach { _ ->
                                            Box(
                                                Modifier
                                                    .size(5.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isSelected) sg.onAccent else sg.accent)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // 마지막 주가 7칸 미만이면 남은 칸을 빈 칸으로 채워 폭을 맞춘다
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f).height(52.dp)) }
                }
            }
            if (isRefreshingMonth) {
                // 월 재조회 중(데이터 있는 갱신) — 캘린더는 그대로 두고 아래에 가는 줄만
                Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = sg.accent, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}

/** 인라인 생성 폼 — 웹 CreateEventForm 미러. 날짜=캘린더 선택일 고정, 시각만 HH:MM 텍스트 */
@Composable
private fun CreateEventForm(
    selectedDay: String,
    isCreating: Boolean,
    createError: String?,
    onCreate: (
        title: String, location: String, description: String,
        startHour: Int, startMinute: Int, endHour: Int?, endMinute: Int?
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors
    var title by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    // 웹 기본값 19:00 미러 — 종료는 빈칸이면 "종료 시각 없는 일정"
    var startTime by rememberSaveable { mutableStateOf("19:00") }
    var endTime by rememberSaveable { mutableStateOf("") }
    var formError by rememberSaveable { mutableStateOf<String?>(null) }
    // "HH:MM"/"H:MM" 허용 — 그 외는 null(형식 오류)
    fun parseTime(text: String): Pair<Int, Int>? {
        val parts = text.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].trim().toIntOrNull() ?: return null
        val minute = parts[1].trim().toIntOrNull() ?: return null
        return if (hour in 0..23 && minute in 0..59) hour to minute else null
    }

    SgCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("새 일정 — $selectedDay", style = SgTheme.typography.titleSmall, color = sg.ink, fontWeight = FontWeight.Bold)
            SgTextField(value = title, onValueChange = { title = it.take(100) }, label = "일정 제목")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SgTextField(
                    value = startTime,
                    onValueChange = { startTime = it },
                    label = "시작(HH:MM)",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
                Text("~", style = SgTheme.typography.bodyMedium, color = sg.inkFaint)
                SgTextField(
                    value = endTime,
                    onValueChange = { endTime = it },
                    label = "종료(선택)",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
            }
            SgTextField(value = location, onValueChange = { location = it.take(200) }, label = "장소 (선택)")
            SgTextField(
                value = description,
                onValueChange = { description = it.take(2000) },
                label = "설명 (선택)",
                singleLine = false,
                minLines = 3
            )
            (formError ?: createError)?.let {
                Text(it, style = SgTheme.typography.bodySmall, color = sg.rust)
            }
            SgPrimaryButton(
                text = "등록",
                onClick = {
                    val start = parseTime(startTime)
                    val end = if (endTime.isBlank()) null else parseTime(endTime)

                    formError = when {
                        start == null -> "시작 시각은 HH:MM 형식으로 입력해 주세요"
                        endTime.isNotBlank() && end == null -> "종료 시각은 HH:MM 형식으로 입력해 주세요"
                        end != null && (end.first < start.first ||
                            (end.first == start.first && end.second < start.second)) ->
                            "종료 시각은 시작 시각보다 빠를 수 없습니다"
                        else -> null
                    }
                    if (formError == null && start != null) {
                        onCreate(title, location, description, start.first, start.second, end?.first, end?.second)
                    }
                },
                enabled = title.isNotBlank(),
                isLoading = isCreating,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** 일정 카드 — 웹 EventCard 미러: 제목·시각·삭제, 장소, 설명, RSVP 3버튼, 집계+참석자 펼침, 작성자 캡션 */
@Composable
private fun EventCard(
    event: GroupEvent,
    isMine: Boolean,
    canModerate: Boolean,
    isBusy: Boolean,
    isExpanded: Boolean,
    // null=아직 못 읽음(펼치면 lazy 조회 중), 빈 목록=응답한 멤버 없음
    attendees: List<kr.hhp227.storygroup.shared.domain.model.EventAttendee>?,
    onAction: (GroupEventsViewModel.Action) -> Unit,
    // 삭제 버튼은 즉시 발화하지 않고 상위(GroupEventsTab)의 확인 다이얼로그를 띄운다
    onRequestDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors

    SgCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    event.title,
                    style = SgTheme.typography.titleSmall,
                    color = sg.ink,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    formatTime(event.startsAt) + (event.endsAt?.let { " ~ ${formatTime(it)}" } ?: ""),
                    style = SgTheme.typography.labelSmall,
                    color = sg.inkFaint
                )
                Spacer(Modifier.weight(1f))
                if (isMine || canModerate) {
                    TextButton(
                        onClick = onRequestDelete,
                        enabled = !isBusy
                    ) {
                        Text("삭제", color = sg.rust, style = SgTheme.typography.labelSmall)
                    }
                }
            }
            event.location?.takeIf { it.isNotBlank() }?.let {
                Text("📍 $it", style = SgTheme.typography.bodySmall, color = sg.inkSoft)
            }
            event.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = SgTheme.typography.bodyMedium, color = sg.ink)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RsvpOptions.forEach { (status, label) ->
                    val selected = event.myRsvp == status

                    if (selected) {
                        Button(
                            onClick = { onAction(GroupEventsViewModel.Action.Rsvp(event, status)) },
                            enabled = !isBusy,
                            shape = SgTheme.shapes.button,
                            colors = ButtonDefaults.buttonColors(backgroundColor = sg.accent, contentColor = sg.onAccent)
                        ) { Text(label, style = SgTheme.typography.labelSmall) }
                    } else {
                        OutlinedButton(
                            onClick = { onAction(GroupEventsViewModel.Action.Rsvp(event, status)) },
                            enabled = !isBusy,
                            shape = SgTheme.shapes.button
                        ) { Text(label, color = sg.ink, style = SgTheme.typography.labelSmall) }
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { onAction(GroupEventsViewModel.Action.ToggleAttendees(event.id)) }
            ) {
                Text(
                    "참석 ${event.goingCount} · 미정 ${event.maybeCount} · 불참 ${event.notGoingCount} " +
                        if (isExpanded) "▲" else "▼",
                    style = SgTheme.typography.labelSmall,
                    color = sg.inkSoft
                )
            }
            if (isExpanded) {
                when {
                    attendees == null -> Text("불러오는 중...", style = SgTheme.typography.labelSmall, color = sg.inkFaint)
                    attendees.isEmpty() -> Text("아직 응답한 멤버가 없습니다.", style = SgTheme.typography.labelSmall, color = sg.inkFaint)
                    else -> attendees.forEach { attendee ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SgAvatar(attendee.name, imageUrl = attendee.profileImg)
                            Text(attendee.name, style = SgTheme.typography.bodySmall, color = sg.ink)
                            Text(
                                RsvpOptions.first { it.first == attendee.status }.second,
                                style = SgTheme.typography.labelSmall,
                                color = sg.inkFaint
                            )
                        }
                    }
                }
            }
            Text("${event.authorName}님이 만든 일정", style = SgTheme.typography.labelSmall, color = sg.inkFaint)
        }
    }
}
