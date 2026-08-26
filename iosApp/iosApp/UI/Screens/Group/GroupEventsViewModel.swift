import Combine
import Foundation
import Shared

/// 일정 탭 — composeApp GroupEventsViewModel.kt와 1:1 미러(웹 /groups/[id]/events 캘린더 페이지 미러,
/// 탭별 VM 분리). 월 앵커 기준 [월초, 다음달 초) 범위를 조회하고, 날짜 귀속·선택 키는 기기 로컬
/// 타임존 yyyy-MM-dd(웹 ymd 미러). RSVP는 낙관적 갱신 없이 서버가 돌려준 집계 갱신 일정으로 카드를
/// 교체하고, 같은 상태 재탭은 취소다. 생성 폼의 날짜는 캘린더 선택일을 그대로 쓴다(M2에 DatePicker가
/// 없는 Compose와 달리 iOS는 시각 입력에 네이티브 DatePicker를 쓰지만, 날짜 자체는 여전히 선택일 고정).
final class GroupEventsViewModel: MviViewModel {
    typealias Event = Never

    @Published private(set) var uiState: UiState

    let groupId: Int64

    private let getGroupEventsUseCase: GetGroupEventsUseCase

    private let getEventDetailUseCase: GetEventDetailUseCase

    private let createEventUseCase: CreateEventUseCase

    private let deleteEventUseCase: DeleteEventUseCase

    private let rsvpEventUseCase: RsvpEventUseCase

    private let cancelEventRsvpUseCase: CancelEventRsvpUseCase

    // 진행 중인 월 조회 Task — 월 이동을 연타해도 이전 요청을 취소해 늦게 도착한 이전 달 응답이
    // 새 달 상태를 덮어쓰지 않게 한다(Kotlin loadJob?.cancel() 레이스 픽스 미러, Finding 1)
    private var loadTask: Task<Void, Never>?

    // 서버 ISO(소수부 자릿수 초과 가능) → Date. TimeFormats.swift엔 재사용 가능한 공개 파서가
    // 없어(relative()가 내부 private 헬퍼로만 씀) 같은 소수부 스트립 방식을 여기 private로 복사한다
    private static let isoFormatter: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter
    }()

    static func parseIso(_ iso: String) -> Date? {
        let secondsOnly = iso.replacingOccurrences(of: "\\.\\d+", with: "", options: .regularExpression)
        return isoFormatter.date(from: secondsOnly)
    }

    static func dateKey(_ date: Date) -> String {
        let parts = Calendar.current.dateComponents([.year, .month, .day], from: date)
        return dateKey(year: parts.year ?? 0, month: parts.month ?? 0, day: parts.day ?? 0)
    }

    /// yyyy-MM-dd 키 조립 — Kotlin dateKeyOf 미러
    static func dateKey(year: Int, month: Int, day: Int) -> String {
        String(format: "%04d-%02d-%02d", year, month, day)
    }

    /// 로컬 연월일시분 → 서버로 보낼 ISO — Kotlin localToIso 미러
    static func localToIso(year: Int, month: Int, day: Int, hour: Int, minute: Int) -> String {
        var components = DateComponents()
        components.year = year
        components.month = month
        components.day = day
        components.hour = hour
        components.minute = minute
        let date = Calendar.current.date(from: components) ?? Date(timeIntervalSince1970: 0)
        return isoFormatter.string(from: date)
    }

    /// 해당 월 1일의 요일(일=0…토=6) — 캘린더 앞쪽 빈 칸 수(Kotlin firstDayOfWeekOfMonth 미러).
    /// Foundation의 .weekday 컴포넌트는 캘린더 firstWeekday 설정과 무관하게 1=일요일 고정이라
    /// java.time DayOfWeek(월=1…일=7)처럼 별도 모듈러 변환이 필요 없다
    static func firstDayOfWeekOfMonth(year: Int, month: Int) -> Int {
        var components = DateComponents()
        components.year = year
        components.month = month
        components.day = 1
        guard let date = Calendar.current.date(from: components) else { return 0 }
        return Calendar.current.component(.weekday, from: date) - 1
    }

    /// 해당 일자의 요일(일=0…토=6) — 월 1일 요일에서 상대 계산(Kotlin dayOfWeek 미러)
    static func dayOfWeek(year: Int, month: Int, day: Int) -> Int {
        (firstDayOfWeekOfMonth(year: year, month: month) + day - 1) % 7
    }

    /// 해당 월의 일수(28~31) — Kotlin monthLength 미러
    static func monthLength(year: Int, month: Int) -> Int {
        var components = DateComponents()
        components.year = year
        components.month = month
        components.day = 1
        guard let date = Calendar.current.date(from: components),
              let range = Calendar.current.range(of: .day, in: .month, for: date) else { return 30 }
        return range.count
    }

    func onAction(_ action: Action) {
        switch action {
        case .refresh: loadMonth()
        case .moveMonth(let delta): moveMonth(delta)
        case .goToday: goToday()
        case .selectDay(let dateKey): uiState.selectedDay = dateKey
        case .toggleCreateForm:
            uiState.showCreateForm.toggle()
            uiState.createError = nil
        case .createEvent(let title, let location, let description, let startHour, let startMinute, let endHour, let endMinute):
            createEvent(title: title, location: location, description: description,
                        startHour: startHour, startMinute: startMinute, endHour: endHour, endMinute: endMinute)
        case .deleteEvent(let eventId): deleteEvent(eventId)
        case .rsvp(let event, let status): rsvp(event, status)
        case .toggleAttendees(let eventId): toggleAttendees(eventId)
        case .dismissActionError: uiState.actionError = nil
        }
    }

    /// 현재 앵커 월 재조회 — [월초, 다음달 초) ISO(웹 loadMonth 미러). 진행 중인 이전 요청은
    /// 무조건 취소하고 교체한다(재진입 가드 없음) — 월 이동을 빠르게 연타해도 늦게 도착한 이전 달
    /// 응답이 새 달 상태를 덮어쓰지 못하게. 취소된 Task는 응답 수신 후·에러 처리 전 각각
    /// Task.isCancelled로 걸러 상태를 쓰지 않는다(Kotlin CancellationException rethrow 미러)
    private func loadMonth() {
        loadTask?.cancel()

        uiState.isLoading = true
        uiState.error = nil
        let year = uiState.year
        let month = uiState.month
        let (nextYear, nextMonth) = month == 12 ? (year + 1, 1) : (year, month + 1)

        loadTask = Task { @MainActor in
            do {
                let events = try await getGroupEventsUseCase.invoke(
                    groupId: groupId,
                    fromIso: Self.localToIso(year: year, month: month, day: 1, hour: 0, minute: 0),
                    toIso: Self.localToIso(year: nextYear, month: nextMonth, day: 1, hour: 0, minute: 0)
                )
                if Task.isCancelled { return }
                uiState.isLoading = false
                uiState.events = events
            } catch {
                if Task.isCancelled { return }
                uiState.isLoading = false
                uiState.error = error.kotlinMessage(fallback: "일정을 불러오지 못했습니다.")
            }
        }
    }

    /// 월 이동 — 이동한 달의 1일을 선택해 아래 목록이 이전 달 잔상을 가리키지 않게 한다(웹 미러).
    /// isLoading은 손대지 않는다 — loadMonth()가 무조건 취소+재시작하므로 재진입 가드용 수동
    /// 리셋이 필요 없다(레이스 픽스로 재진입 가드 자체가 사라졌다)
    private func moveMonth(_ delta: Int) {
        let zeroBased = uiState.year * 12 + (uiState.month - 1) + delta
        let year = zeroBased / 12
        let month = zeroBased % 12 + 1

        uiState.year = year
        uiState.month = month
        uiState.selectedDay = Self.dateKey(year: year, month: month, day: 1)
        uiState.events = nil
        loadMonth()
    }

    /// 오늘로 — 다른 달이면 재조회, 같은 달이면 선택만 이동(웹 goToday 미러)
    private func goToday() {
        let today = Calendar.current.dateComponents([.year, .month, .day], from: Date())
        guard let year = today.year, let month = today.month, let day = today.day else { return }
        let key = Self.dateKey(year: year, month: month, day: day)

        if year != uiState.year || month != uiState.month {
            uiState.year = year
            uiState.month = month
            uiState.selectedDay = key
            uiState.events = nil
            loadMonth()
        } else {
            uiState.selectedDay = key
        }
    }

    /// 생성 — 날짜는 캘린더 선택일 고정이라 결과는 항상 현재 달(목록 삽입+정렬만)
    private func createEvent(
        title: String, location: String, description: String,
        startHour: Int, startMinute: Int, endHour: Int?, endMinute: Int?
    ) {
        if uiState.isCreating { return }

        let parts = uiState.selectedDay.split(separator: "-").compactMap { Int($0) }
        guard parts.count == 3 else { return }
        var endsAtIso: String? = nil
        if let endHour = endHour, let endMinute = endMinute {
            // 종료가 시작보다 빠르면 폼 검증에서 걸렀어야 한다 — 방어적으로 한 번 더(Kotlin 미러)
            if endHour < startHour || (endHour == startHour && endMinute < startMinute) {
                uiState.createError = "종료 시각은 시작 시각보다 빠를 수 없습니다"
                return
            }
            endsAtIso = Self.localToIso(year: parts[0], month: parts[1], day: parts[2], hour: endHour, minute: endMinute)
        }

        uiState.isCreating = true
        uiState.createError = nil
        Task { @MainActor in
            do {
                let created = try await createEventUseCase.invoke(
                    groupId: groupId,
                    title: title,
                    description: description.isEmpty ? nil : description,
                    location: location.isEmpty ? nil : location,
                    startsAtIso: Self.localToIso(year: parts[0], month: parts[1], day: parts[2], hour: startHour, minute: startMinute),
                    endsAtIso: endsAtIso
                )
                uiState.isCreating = false
                uiState.showCreateForm = false
                uiState.events = ((uiState.events ?? []) + [created]).sorted { $0.startsAt < $1.startsAt }
            } catch {
                uiState.isCreating = false
                uiState.createError = error.kotlinMessage(fallback: "일정을 만들지 못했습니다.")
            }
        }
    }

    /// 삭제(작성자/모더레이터 — 서버 검증) — 성공 시 목록에서 제거(웹 handleDelete 미러)
    private func deleteEvent(_ eventId: Int64) {
        if uiState.busyEventId != nil { return }

        uiState.busyEventId = eventId
        uiState.actionError = nil
        Task { @MainActor in
            do {
                try await deleteEventUseCase.invoke(groupId: groupId, eventId: eventId)
                uiState.busyEventId = nil
                uiState.events = uiState.events?.filter { $0.id != eventId }
            } catch {
                uiState.busyEventId = nil
                uiState.actionError = error.kotlinMessage(fallback: "일정 삭제에 실패했습니다.")
            }
        }
    }

    /// RSVP — 같은 상태 재탭=취소(웹 handleRsvp 미러). 서버가 돌려준 집계 갱신 일정으로 카드를 교체한다
    private func rsvp(_ event: GroupEvent, _ status: RsvpStatus) {
        if uiState.busyEventId != nil { return }

        uiState.busyEventId = event.id
        uiState.actionError = nil
        Task { @MainActor in
            do {
                let updated = event.myRsvp == status
                    ? try await cancelEventRsvpUseCase.invoke(groupId: groupId, eventId: event.id)
                    : try await rsvpEventUseCase.invoke(groupId: groupId, eventId: event.id, status: status)
                uiState.busyEventId = nil
                uiState.events = uiState.events?.map { $0.id == updated.id ? updated : $0 }
                // 명단이 바뀌었으니 펼쳐볼 때 다시 불러온다(웹 setAttendees(null) 미러)
                uiState.attendeesByEvent.removeValue(forKey: event.id)
            } catch {
                uiState.busyEventId = nil
                uiState.actionError = error.kotlinMessage(fallback: "참석 응답에 실패했습니다.")
            }
        }
    }

    /// 참석자 펼침 — 캐시 없으면 단건 조회, 실패는 조용히 빈 목록(부가 정보 — 웹 미러)
    private func toggleAttendees(_ eventId: Int64) {
        if uiState.expandedEventIds.contains(eventId) {
            uiState.expandedEventIds.remove(eventId)
            return
        }
        uiState.expandedEventIds.insert(eventId)
        if uiState.attendeesByEvent[eventId] == nil {
            Task { @MainActor in
                let attendees = (try? await getEventDetailUseCase.invoke(groupId: groupId, eventId: eventId).attendees) ?? []
                uiState.attendeesByEvent[eventId] = attendees
            }
        }
    }

    init(
        groupId: Int64,
        getGroupEventsUseCase: GetGroupEventsUseCase = AppContainer.shared.getGroupEventsUseCase,
        getEventDetailUseCase: GetEventDetailUseCase = AppContainer.shared.getEventDetailUseCase,
        createEventUseCase: CreateEventUseCase = AppContainer.shared.createEventUseCase,
        deleteEventUseCase: DeleteEventUseCase = AppContainer.shared.deleteEventUseCase,
        rsvpEventUseCase: RsvpEventUseCase = AppContainer.shared.rsvpEventUseCase,
        cancelEventRsvpUseCase: CancelEventRsvpUseCase = AppContainer.shared.cancelEventRsvpUseCase,
        getCurrentUserIdUseCase: GetCurrentUserIdUseCase = AppContainer.shared.getCurrentUserIdUseCase
    ) {
        self.groupId = groupId
        self.getGroupEventsUseCase = getGroupEventsUseCase
        self.getEventDetailUseCase = getEventDetailUseCase
        self.createEventUseCase = createEventUseCase
        self.deleteEventUseCase = deleteEventUseCase
        self.rsvpEventUseCase = rsvpEventUseCase
        self.cancelEventRsvpUseCase = cancelEventRsvpUseCase
        let today = Calendar.current.dateComponents([.year, .month, .day], from: Date())
        uiState = UiState(
            myUserId: getCurrentUserIdUseCase.invoke()?.int64Value,
            year: today.year ?? 0,
            month: today.month ?? 0,
            selectedDay: Self.dateKey(year: today.year ?? 0, month: today.month ?? 0, day: today.day ?? 0)
        )
        loadMonth()
    }

    struct UiState {
        // 일정 카드의 "작성자 본인" 삭제 판정 — 세션이 있는 한 nil이 아니다
        var myUserId: Int64? = nil
        // 월 앵커(연/월) — 범위 조회와 캘린더 그리드의 기준
        var year: Int = 0
        var month: Int = 0
        // 선택일 키(yyyy-MM-dd, 로컬) — 셀 강조와 아래 목록·생성 폼 날짜의 기준
        var selectedDay: String = ""
        // nil=이 달을 아직 못 읽음(로딩/실패) — 빈 목록과 구분한다(웹 events === null 미러)
        var events: [GroupEvent]? = nil
        var isLoading = false
        var error: String? = nil
        var showCreateForm = false
        var isCreating = false
        var createError: String? = nil
        // 카드 액션(RSVP/삭제) 진행 중 일정 — 동시에 하나만(멤버 탭 processingRequestUserId 선례)
        var busyEventId: Int64? = nil
        // RSVP/삭제 실패 문구 — 로드 에러(error)와 달리 캘린더를 대체하지 않는다
        var actionError: String? = nil
        var expandedEventIds: Set<Int64> = []
        // 일정별 참석자 캐시 — RSVP가 바뀌면 그 일정 키를 비워 다음 펼침에 재조회한다
        var attendeesByEvent: [Int64: [EventAttendee]] = [:]

        /// 로컬 날짜 키별 일정 — 캘린더 점·선택일 목록의 원천(웹 eventsByDay 미러). 접근마다
        /// groupBy를 다시 도는 계산 프로퍼티라 — 소비 측(GroupEventsTab)이 1회만 읽어 재사용해야
        /// 한다(Kotlin Finding 2 레이스 픽스 미러, 여기 자체는 손대지 않는다)
        var eventsByDay: [String: [GroupEvent]] {
            Dictionary(grouping: events ?? []) { event in
                GroupEventsViewModel.parseIso(event.startsAt).map(GroupEventsViewModel.dateKey)
                    ?? String(event.startsAt.prefix(10))
            }
        }

        var selectedEvents: [GroupEvent] { eventsByDay[selectedDay] ?? [] }
    }

    enum Action {
        case refresh
        case moveMonth(Int)
        case goToday
        case selectDay(String)
        case toggleCreateForm
        case createEvent(title: String, location: String, description: String,
                         startHour: Int, startMinute: Int, endHour: Int?, endMinute: Int?)
        case deleteEvent(Int64)
        case rsvp(GroupEvent, RsvpStatus)
        case toggleAttendees(Int64)
        case dismissActionError
    }
}
