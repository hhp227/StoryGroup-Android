import Combine
import Foundation
import Shared

/// 알림 — composeApp NotificationsViewModel.kt와 1:1 미러.
/// 목록은 UiState에 담기는 최신 PagingData(다른 페이징 화면과 동일), 미읽음 수는 unread-count API로
/// 별도 로드(첫 페이지 밖 미읽음까지 반영 — "모두 읽음 처리" 노출 기준). 진입마다 refresh가 미읽음 수를
/// 다시 읽고 Event.refreshList로 목록도 첫 페이지부터 다시 읽는다(신선도 우선).
/// 단건 읽음은 서버 재조회 없이 readOverrides로 낙관 갱신한다(그룹 탐색 localOverrides 패턴).
final class NotificationsViewModel: MviViewModel {
    @Published private(set) var uiState = UiState()

    let event = PassthroughSubject<Event, Never>()

    private var cancellables = Set<AnyCancellable>()

    private let getUnreadNotificationCountUseCase: GetUnreadNotificationCountUseCase

    private let markNotificationAsReadUseCase: MarkNotificationAsReadUseCase

    private let markAllNotificationsAsReadUseCase: MarkAllNotificationsAsReadUseCase

    private func setPagingData(_ pagingData: PagingData<AppNotification>) {
        uiState.pagingData = pagingData
    }

    func onAction(_ action: Action) {
        switch action {
        case .refresh: refresh()
        case .markAsRead(let notificationId): markAsRead(notificationId: notificationId)
        case .markAllAsRead: markAllAsRead()
        }
    }

    /// 진입 시 발화 — 미읽음 수 재조회+목록 새로고침(목록 자체는 Pager가 로드/재시도)
    private func refresh() {
        event.send(.refreshList)
        Task { @MainActor in
            // 미읽음 수는 보조 정보 — 실패해도 목록은 그려지므로 이전 값을 유지하고 조용히 넘어간다
            if let count = try? await getUnreadNotificationCountUseCase.invoke() {
                uiState.unreadCount = count.int64Value
            }
        }
    }

    /// 단건 읽음 — 성공 시 목록 재조회 없이 해당 항목만 낙관 갱신한다(스크롤 보존)
    private func markAsRead(notificationId: Int64) {
        if uiState.processingId != nil { return }

        uiState.processingId = notificationId
        uiState.actionError = nil
        Task { @MainActor in
            do {
                try await markNotificationAsReadUseCase.invoke(notificationId: notificationId)
                uiState.processingId = nil
                uiState.readOverrides.insert(notificationId)
                uiState.unreadCount = max(0, uiState.unreadCount - 1)
            } catch {
                uiState.processingId = nil
                uiState.actionError = error.kotlinMessage(fallback: "읽음 처리에 실패했습니다.")
            }
        }
    }

    /// 전체 읽음 — 성공 시 목록을 첫 페이지부터 다시 읽어 서버 상태를 그대로 반영한다(웹 미러)
    private func markAllAsRead() {
        if uiState.isMarkingAll { return }

        uiState.isMarkingAll = true
        uiState.actionError = nil
        Task { @MainActor in
            do {
                try await markAllNotificationsAsReadUseCase.invoke()
                uiState.isMarkingAll = false
                uiState.unreadCount = 0
                uiState.readOverrides = []
                event.send(.refreshList)
            } catch {
                uiState.isMarkingAll = false
                uiState.actionError = error.kotlinMessage(fallback: "전체 읽음 처리에 실패했습니다.")
            }
        }
    }

    init(
        getNotificationsPagingDataUseCase: GetNotificationsPagingDataUseCase,
        getUnreadNotificationCountUseCase: GetUnreadNotificationCountUseCase,
        markNotificationAsReadUseCase: MarkNotificationAsReadUseCase,
        markAllNotificationsAsReadUseCase: MarkAllNotificationsAsReadUseCase
    ) {
        self.getUnreadNotificationCountUseCase = getUnreadNotificationCountUseCase
        self.markNotificationAsReadUseCase = markNotificationAsReadUseCase
        self.markAllNotificationsAsReadUseCase = markAllNotificationsAsReadUseCase

        // UseCase는 cachedIn 없는 Flow를 반환하므로 프레젠테이션 경계인 여기서 캐시를 적용한다
        getNotificationsPagingDataUseCase()
            .cachedIn()
            .sink { [weak self] in self?.setPagingData($0) }
            .store(in: &cancellables)
    }

    struct UiState {
        // Kotlin의 PagingData.empty() 대응 — ObjC 제네릭 클래스에는 static 확장을 못 붙여 브리지 함수 직접 호출
        var pagingData: PagingData<AppNotification> = NotificationBridgesKt.emptyAppNotificationPagingData()
        // unread-count API 값 — 첫 페이지 밖 미읽음까지 포함("모두 읽음 처리" 노출 기준)
        var unreadCount: Int64 = 0
        // 단건 읽음 직후 서버 재조회 없이 항목 표시만 낙관적으로 덮어쓴다(그룹 탐색 localOverrides 패턴)
        var readOverrides: Set<Int64> = []
        // 읽음 버튼 로딩 표시용 — 동시에 하나만 처리(가입 신청 인박스와 동일)
        var processingId: Int64? = nil
        var isMarkingAll = false
        // 읽음 처리 실패 문구 — 목록 로드 에러(Paging LoadState)와 달리 목록을 대체하지 않는다
        var actionError: String? = nil

        func isRead(_ notification: AppNotification) -> Bool {
            notification.isRead || readOverrides.contains(notification.id)
        }
    }

    enum Action {
        case refresh
        case markAsRead(notificationId: Int64)
        case markAllAsRead
    }

    enum Event {
        /// 목록 새로고침 — 화면이 프레젠터 refresh()로 첫 페이지부터 다시 읽는다(홈 피드와 동일 패턴)
        case refreshList
    }
}
