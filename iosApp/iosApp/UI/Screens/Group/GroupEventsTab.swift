import Foundation
import SwiftUI
import Shared

// 웹 DAY_LABELS 미러 — 일=rust, 토=accent 강조는 셀에서 인덱스로 판정
private let dayLabels = ["일", "월", "화", "수", "목", "금", "토"]

// (상태, 라벨) 순서 고정 — Compose RsvpOptions 미러. ForEach는 오프셋 id를 쓴다(브리지 enum Hashable 회피)
private let rsvpOptions: [(status: RsvpStatus, label: String)] = [
    (.going, "참석"),
    (.maybe, "미정"),
    (.notGoing, "불참")
]

/// "HH:mm" — 이벤트 카드 시각 표기(웹 formatTime 미러, 로컬 타임존)
private func formatTime(_ iso: String) -> String {
    guard let date = GroupEventsViewModel.parseIso(iso) else { return "" }
    let comps = Calendar.current.dateComponents([.hour, .minute], from: date)
    return String(format: "%02d:%02d", comps.hour ?? 0, comps.minute ?? 0)
}

/// 일정 탭 — Compose GroupEventsTab.kt와 1:1 미러: 월 캘린더 카드+인라인 생성 폼+선택일 일정 카드
/// 목록. 생성 폼의 날짜는 캘린더 선택일을 그대로 쓰고, 시각 입력만 네이티브 DatePicker(iOS 관용
/// 예외 — Compose는 HH:MM 텍스트). 다른 그룹 상세 탭(GroupAlbumTab 등)과 같이 자체 ScrollView를
/// 두지 않는다 — GroupDetailView의 바깥 ScrollView 하나에 얹힌다.
struct GroupEventsTab: View {
    @ObservedObject var viewModel: GroupEventsViewModel

    // 일정 삭제 버튼 노출(작성자 본인 외) — 화면이 상세 VM의 canModerate로 게이트
    let canModerate: Bool

    @Environment(\.sgColors) private var colors

    var body: some View {
        // eventsByDay는 UiState의 계산 프로퍼티라 접근마다 groupBy를 다시 돈다(Compose Finding 2
        // 레이스 픽스의 Swift measure) — 여기서 한 번만 계산해 캘린더 셀 루프·선택일 목록에 재사용한다
        let eventsByDay = viewModel.uiState.eventsByDay
        let selectedEvents = eventsByDay[viewModel.uiState.selectedDay] ?? []

        VStack(alignment: .leading, spacing: 12) {
            CalendarCard(
                year: viewModel.uiState.year,
                month: viewModel.uiState.month,
                selectedDay: viewModel.uiState.selectedDay,
                eventsByDay: eventsByDay,
                showCreateForm: viewModel.uiState.showCreateForm,
                isRefreshingMonth: viewModel.uiState.isLoading && viewModel.uiState.events != nil,
                onAction: viewModel.onAction
            )
            .padding(.horizontal, 16)

            if viewModel.uiState.showCreateForm {
                CreateEventForm(
                    selectedDay: viewModel.uiState.selectedDay,
                    isCreating: viewModel.uiState.isCreating,
                    createError: viewModel.uiState.createError,
                    onCreate: { title, location, description, startHour, startMinute, endHour, endMinute in
                        viewModel.onAction(.createEvent(
                            title: title, location: location, description: description,
                            startHour: startHour, startMinute: startMinute, endHour: endHour, endMinute: endMinute
                        ))
                    }
                )
                .padding(.horizontal, 16)
            }

            selectedDayHeader
                .padding(.horizontal, 16)

            if let message = viewModel.uiState.actionError {
                HStack(alignment: .center) {
                    Text(message).font(.subheadline).foregroundColor(colors.rust)
                    Spacer()
                    Button("닫기") { viewModel.onAction(.dismissActionError) }
                        .font(.subheadline)
                        .foregroundColor(colors.accent)
                }
                .padding(.horizontal, 16)
            }

            eventsSection(selectedEvents: selectedEvents)
        }
        .padding(.vertical, 12)
    }

    /// "M월 d일 (요일)" — 웹 선택일 헤더 미러
    @ViewBuilder private var selectedDayHeader: some View {
        let parts = viewModel.uiState.selectedDay.split(separator: "-").compactMap { Int($0) }

        if parts.count == 3 {
            let weekday = GroupEventsViewModel.dayOfWeek(year: parts[0], month: parts[1], day: parts[2])
            Text("\(parts[1])월 \(parts[2])일 (\(dayLabels[weekday]))")
                .font(.subheadline.bold())
                .foregroundColor(colors.ink)
        }
    }

    @ViewBuilder
    private func eventsSection(selectedEvents: [GroupEvent]) -> some View {
        if viewModel.uiState.events == nil, let error = viewModel.uiState.error {
            VStack(spacing: 8) {
                Text(error).font(.subheadline).foregroundColor(colors.rust)
                Button("다시 시도") { viewModel.onAction(.refresh) }
                    .font(.subheadline)
                    .foregroundColor(colors.accent)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 24)
        } else if viewModel.uiState.events == nil {
            HStack {
                Spacer()
                ProgressView()
                Spacer()
            }
            .padding(.vertical, 24)
        } else if selectedEvents.isEmpty {
            SGEmptyState(title: "이 날짜에는 일정이 없습니다", subtitle: "일정 만들기로 첫 일정을 등록해 보세요.")
                .padding(.vertical, 24)
        } else {
            ForEach(selectedEvents, id: \.id) { event in
                EventCard(
                    event: event,
                    isMine: event.userId == viewModel.uiState.myUserId,
                    canModerate: canModerate,
                    isBusy: viewModel.uiState.busyEventId != nil,
                    isExpanded: viewModel.uiState.expandedEventIds.contains(event.id),
                    attendees: viewModel.uiState.attendeesByEvent[event.id],
                    onAction: viewModel.onAction
                )
                .padding(.horizontal, 16)
            }
        }
    }
}

/// 월 이동 헤더+7열 그리드 — 웹 캘린더 카드 미러(오늘=테두리, 선택=accent 배경, 점 최대 3개).
/// 캘린더가 실제 쓰는 값만 받는다(Compose Finding 3 미러) — viewModel.uiState 전체를 넘기면
/// RSVP/삭제 등 무관한 변화에도 ~35셀 그리드가 매번 다시 그려진다
private struct CalendarCard: View {
    let year: Int

    let month: Int

    let selectedDay: String

    let eventsByDay: [String: [GroupEvent]]

    let showCreateForm: Bool

    let isRefreshingMonth: Bool

    let onAction: (GroupEventsViewModel.Action) -> Void

    @Environment(\.sgColors) private var colors

    private static let columns = Array(repeating: GridItem(.flexible(), spacing: 2), count: 7)

    var body: some View {
        let todayKey = GroupEventsViewModel.dateKey(Date())
        let leadingBlanks = GroupEventsViewModel.firstDayOfWeekOfMonth(year: year, month: month)
        let daysInMonth = GroupEventsViewModel.monthLength(year: year, month: month)

        SGCard {
            VStack(spacing: 8) {
                header
                weekdayLabels
                LazyVGrid(columns: Self.columns, spacing: 2) {
                    ForEach(0..<leadingBlanks, id: \.self) { _ in
                        Color.clear.frame(height: 52)
                    }
                    ForEach(1...daysInMonth, id: \.self) { day in
                        dayCell(day, todayKey: todayKey)
                    }
                }
                if isRefreshingMonth {
                    // 월 재조회 중(데이터 있는 갱신) — 캘린더는 그대로 두고 아래에 가는 줄만
                    HStack {
                        Spacer()
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: colors.accent))
                            .scaleEffect(0.7)
                        Spacer()
                    }
                    .padding(.top, 4)
                }
            }
            .padding(12)
        }
    }

    private var header: some View {
        HStack(spacing: 4) {
            Button(action: { onAction(.moveMonth(-1)) }) {
                Image(systemName: "chevron.left").foregroundColor(colors.inkSoft)
            }
            .accessibilityLabel("이전 달")
            Text("\(year)년 \(month)월")
                .font(.subheadline.bold())
                .foregroundColor(colors.ink)
            Button(action: { onAction(.moveMonth(1)) }) {
                Image(systemName: "chevron.right").foregroundColor(colors.inkSoft)
            }
            .accessibilityLabel("다음 달")
            Button("오늘") { onAction(.goToday) }
                .font(.subheadline)
                .foregroundColor(colors.accent)
            Spacer()
            Button(showCreateForm ? "닫기" : "일정 만들기") { onAction(.toggleCreateForm) }
                .font(.subheadline.bold())
                .foregroundColor(colors.accent)
        }
    }

    private var weekdayLabels: some View {
        HStack(spacing: 0) {
            ForEach(Array(dayLabels.enumerated()), id: \.offset) { index, label in
                Text(label)
                    .font(.caption.bold())
                    .foregroundColor(weekdayColor(index))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 4)
            }
        }
    }

    private func weekdayColor(_ index: Int) -> Color {
        switch index {
        case 0: return colors.rust
        case 6: return colors.accent
        default: return colors.inkFaint
        }
    }

    private func dayCell(_ day: Int, todayKey: String) -> some View {
        let key = GroupEventsViewModel.dateKey(year: year, month: month, day: day)
        let isSelected = key == selectedDay
        let isToday = key == todayKey
        let dayEvents = eventsByDay[key] ?? []

        return Button(action: { onAction(.selectDay(key)) }) {
            VStack(spacing: 2) {
                Text("\(day)")
                    .font(.subheadline)
                    .fontWeight(isToday || isSelected ? .bold : .regular)
                    .foregroundColor(isSelected ? colors.onAccent : colors.ink)
                if !dayEvents.isEmpty {
                    HStack(spacing: 2) {
                        ForEach(0..<min(dayEvents.count, 3), id: \.self) { _ in
                            Circle()
                                .fill(isSelected ? colors.onAccent : colors.accent)
                                .frame(width: 5, height: 5)
                        }
                    }
                }
            }
            .padding(.top, 6)
            .frame(maxWidth: .infinity)
            .frame(height: 52)
            .background(
                RoundedRectangle(cornerRadius: 8)
                    .fill(isSelected ? colors.accent : Color.clear)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(isToday ? colors.accent : Color.clear, lineWidth: 1.5)
            )
        }
        .buttonStyle(.plain)
    }
}

/// 인라인 생성 폼 — 웹 CreateEventForm 미러. 날짜=캘린더 선택일 고정. 시각은 Compose가 HH:MM
/// 텍스트를 쓰는 것과 달리 네이티브 DatePicker(.hourAndMinute) — 브리프 Step 3 명시 플랫폼 예외.
/// 형식 오류는 원천적으로 없으므로 Compose의 "HH:MM 형식으로 입력해 주세요" 문구는 옮기지 않고,
/// "종료가 시작보다 빠름" 같은 비즈니스 검증 문구만 그대로 미러한다.
private struct CreateEventForm: View {
    let selectedDay: String

    let isCreating: Bool

    let createError: String?

    let onCreate: (
        _ title: String, _ location: String, _ description: String,
        _ startHour: Int, _ startMinute: Int, _ endHour: Int?, _ endMinute: Int?
    ) -> Void

    @Environment(\.sgColors) private var colors

    @State private var title = ""

    @State private var location = ""

    @State private var description = ""

    // 웹 기본값 19:00 미러
    @State private var startTime: Date = CreateEventForm.defaultStartTime()

    @State private var addEndTime = false

    @State private var endTime: Date = CreateEventForm.defaultStartTime()

    @State private var formError: String?

    var body: some View {
        SGCard {
            VStack(alignment: .leading, spacing: 12) {
                Text("새 일정 — \(selectedDay)")
                    .font(.subheadline.bold())
                    .foregroundColor(colors.ink)
                SGTextField(label: "일정 제목", text: $title)
                    .onChange(of: title) { if $0.count > 100 { title = String($0.prefix(100)) } }
                VStack(alignment: .leading, spacing: 6) {
                    Text("시작 시각").font(.caption.bold()).foregroundColor(colors.inkSoft)
                    DatePicker("", selection: $startTime, displayedComponents: .hourAndMinute)
                        .labelsHidden()
                }
                Toggle("종료 시각 추가", isOn: $addEndTime)
                    .font(.subheadline)
                    .foregroundColor(colors.ink)
                if addEndTime {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("종료 시각").font(.caption.bold()).foregroundColor(colors.inkSoft)
                        DatePicker("", selection: $endTime, displayedComponents: .hourAndMinute)
                            .labelsHidden()
                    }
                }
                SGTextField(label: "장소 (선택)", text: $location)
                    .onChange(of: location) { if $0.count > 200 { location = String($0.prefix(200)) } }
                VStack(alignment: .leading, spacing: 6) {
                    Text("설명 (선택)").font(.caption.bold()).foregroundColor(colors.inkSoft)
                    // 멀티라인은 TextEditor(웹 textarea 미러) — SGTextField는 싱글라인뿐이라
                    // CreateGroupView/AccountSettingsView와 같은 타협(linen 채움 대신 보더만)
                    TextEditor(text: $description)
                        .frame(minHeight: 72)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(RoundedRectangle(cornerRadius: 10).stroke(colors.stoneBorder, lineWidth: 1))
                        .onChange(of: description) { if $0.count > 2000 { description = String($0.prefix(2000)) } }
                }
                if let message = formError ?? createError {
                    Text(message).font(.caption).foregroundColor(colors.rust)
                }
                SGPrimaryButton(
                    title: "등록",
                    enabled: !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                    isLoading: isCreating,
                    action: submit
                )
            }
            .padding(16)
        }
    }

    private func submit() {
        let start = Calendar.current.dateComponents([.hour, .minute], from: startTime)
        guard let startHour = start.hour, let startMinute = start.minute else { return }
        var endHour: Int? = nil
        var endMinute: Int? = nil

        if addEndTime {
            let end = Calendar.current.dateComponents([.hour, .minute], from: endTime)
            guard let hour = end.hour, let minute = end.minute else { return }
            // 종료 시각은 시작 시각보다 빠를 수 없다(Compose 검증 문구 그대로 미러)
            if hour < startHour || (hour == startHour && minute < startMinute) {
                formError = "종료 시각은 시작 시각보다 빠를 수 없습니다"
                return
            }
            endHour = hour
            endMinute = minute
        }
        formError = nil
        onCreate(title, location, description, startHour, startMinute, endHour, endMinute)
    }

    private static func defaultStartTime() -> Date {
        var components = Calendar.current.dateComponents([.year, .month, .day], from: Date())
        components.hour = 19
        components.minute = 0
        return Calendar.current.date(from: components) ?? Date()
    }
}

/// 일정 카드 — 웹 EventCard 미러: 제목·시각·삭제, 장소, 설명, RSVP 3버튼, 집계+참석자 펼침, 작성자 캡션.
/// 삭제는 Compose와 달리 확인 alert을 거친다(iOS 15 타깃 — confirmationDialog 대신 .alert, 브리프 Step 3)
private struct EventCard: View {
    let event: GroupEvent

    let isMine: Bool

    let canModerate: Bool

    let isBusy: Bool

    let isExpanded: Bool

    // nil=아직 못 읽음(펼치면 lazy 조회 중), 빈 목록=응답한 멤버 없음
    let attendees: [EventAttendee]?

    let onAction: (GroupEventsViewModel.Action) -> Void

    @Environment(\.sgColors) private var colors

    @State private var showDeleteConfirm = false

    var body: some View {
        SGCard {
            VStack(alignment: .leading, spacing: 8) {
                HStack(alignment: .center, spacing: 8) {
                    Text(event.title)
                        .font(.subheadline.bold())
                        .foregroundColor(colors.ink)
                        .lineLimit(1)
                    Text(timeRangeText)
                        .font(.caption)
                        .foregroundColor(colors.inkFaint)
                    Spacer()
                    if isMine || canModerate {
                        Button("삭제") { showDeleteConfirm = true }
                            .font(.caption)
                            .foregroundColor(colors.rust)
                            .disabled(isBusy)
                    }
                }
                // Kotlin data class의 "description"은 NSObject.description과 충돌해 description_로 브리지된다
                if let location = event.location, !location.isEmpty {
                    Text("📍 \(location)").font(.subheadline).foregroundColor(colors.inkSoft)
                }
                if let description = event.description_, !description.isEmpty {
                    Text(description).font(.subheadline).foregroundColor(colors.ink)
                }
                HStack(spacing: 8) {
                    ForEach(Array(rsvpOptions.enumerated()), id: \.offset) { _, option in
                        rsvpButton(status: option.status, label: option.label)
                    }
                }
                Button(action: { onAction(.toggleAttendees(event.id)) }) {
                    Text(
                        "참석 \(event.goingCount) · 미정 \(event.maybeCount) · 불참 \(event.notGoingCount) "
                            + (isExpanded ? "▲" : "▼")
                    )
                    .font(.caption)
                    .foregroundColor(colors.inkSoft)
                }
                .buttonStyle(.plain)
                if isExpanded {
                    attendeesList
                }
                Text("\(event.authorName)님이 만든 일정")
                    .font(.caption)
                    .foregroundColor(colors.inkFaint)
            }
            .padding(16)
        }
        .alert("일정을 삭제할까요?", isPresented: $showDeleteConfirm) {
            Button("취소", role: .cancel) {}
            Button("삭제", role: .destructive) { onAction(.deleteEvent(event.id)) }
        }
    }

    private var timeRangeText: String {
        let start = formatTime(event.startsAt)
        guard let endsAt = event.endsAt else { return start }
        return "\(start) ~ \(formatTime(endsAt))"
    }

    private func rsvpButton(status: RsvpStatus, label: String) -> some View {
        let selected = event.myRsvp == status

        return Button(action: { onAction(.rsvp(event, status)) }) {
            Text(label)
                .font(.caption)
                .foregroundColor(selected ? colors.onAccent : colors.ink)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(
                    RoundedRectangle(cornerRadius: colors.radiusButton ?? 20, style: .continuous)
                        .fill(selected ? colors.accent : Color.clear)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: colors.radiusButton ?? 20, style: .continuous)
                        .stroke(selected ? Color.clear : colors.stoneBorder, lineWidth: 1)
                )
        }
        .buttonStyle(.plain)
        .disabled(isBusy)
    }

    @ViewBuilder private var attendeesList: some View {
        if let attendees = attendees {
            if attendees.isEmpty {
                Text("아직 응답한 멤버가 없습니다.").font(.caption).foregroundColor(colors.inkFaint)
            } else {
                ForEach(attendees, id: \.userId) { attendee in
                    HStack(spacing: 8) {
                        SGAvatar(name: attendee.name, imageUrl: attendee.profileImg)
                        Text(attendee.name).font(.subheadline).foregroundColor(colors.ink)
                        Text(rsvpLabel(attendee.status)).font(.caption).foregroundColor(colors.inkFaint)
                    }
                }
            }
        } else {
            Text("불러오는 중...").font(.caption).foregroundColor(colors.inkFaint)
        }
    }

    private func rsvpLabel(_ status: RsvpStatus) -> String {
        rsvpOptions.first { $0.status == status }?.label ?? ""
    }
}
