import Combine
import Foundation
import Shared

/// 그룹 신고함(모더레이터 전용) — composeApp GroupReportsViewModel.kt와 1:1 미러
/// (웹 /groups/[id]/reports). 필터(대기중/전체)와 확인/기각 처리. 처리 성공은 대기중
/// 필터에선 행 제거, 전체에선 상태만 갱신(웹과 동일). 화면 전환은 뷰 직행이라 Event=Never.
final class GroupReportsViewModel: MviViewModel {
    typealias Event = Never

    @Published private(set) var uiState = UiState()

    let groupId: Int64

    private let getGroupReportsUseCase: GetGroupReportsUseCase

    private let processGroupReportUseCase: ProcessGroupReportUseCase

    func onAction(_ action: Action) {
        switch action {
        case .refresh: refresh()
        case .setFilter(let filter): setFilter(filter)
        case .process(let reportId, let status): process(reportId, status)
        case .dismissActionError: uiState.actionError = nil
        }
    }

    private func refresh() {
        if uiState.isLoading { return }

        uiState.isLoading = true
        uiState.loadError = nil
        Task { @MainActor in
            do {
                let reports = try await getGroupReportsUseCase.invoke(groupId: groupId, status: uiState.filter)
                uiState.isLoading = false
                uiState.reports = reports
            } catch {
                uiState.isLoading = false
                uiState.loadError = error.kotlinMessage(fallback: "신고 목록을 불러오지 못했습니다.")
            }
        }
    }

    /// 필터 전환 — 목록을 비우고 다시 읽는다(웹의 key 리마운트 미러)
    private func setFilter(_ filter: ReportStatus?) {
        if uiState.filter == filter { return }

        uiState.filter = filter
        uiState.reports = nil
        uiState.isLoading = false
        uiState.loadError = nil
        refresh()
    }

    private func process(_ reportId: Int64, _ status: ReportStatus) {
        if uiState.busyReportId != nil { return }

        uiState.busyReportId = reportId
        uiState.actionError = nil
        Task { @MainActor in
            do {
                let updated = try await processGroupReportUseCase.invoke(groupId: groupId, reportId: reportId, status: status)
                uiState.busyReportId = nil
                // 대기중 필터에서는 처리된 행이 빠지고, 전체에서는 상태만 갱신된다(웹 미러)
                uiState.reports = uiState.reports?.compactMap { report in
                    if report.id != reportId { return report }
                    return uiState.filter == .pending ? nil : updated
                }
            } catch {
                uiState.busyReportId = nil
                uiState.actionError = error.kotlinMessage(fallback: "신고 처리에 실패했습니다.")
            }
        }
    }

    init(groupId: Int64, getGroupReportsUseCase: GetGroupReportsUseCase, processGroupReportUseCase: ProcessGroupReportUseCase) {
        self.groupId = groupId
        self.getGroupReportsUseCase = getGroupReportsUseCase
        self.processGroupReportUseCase = processGroupReportUseCase
        refresh()
    }

    struct UiState {
        // nil=아직 로드 전 — 빈 목록(신고 없음)과 구분한다
        var reports: [PostReport]? = nil
        var isLoading = false
        var loadError: String? = nil
        // 기본 대기중 — nil이면 전체(웹 필터 칩 미러)
        var filter: ReportStatus? = .pending
        // 처리 진행 중인 행 — 그 행의 버튼만 잠근다(웹 busyFor 미러)
        var busyReportId: Int64? = nil
        var actionError: String? = nil
    }

    enum Action {
        case refresh
        case setFilter(ReportStatus?)
        case process(Int64, ReportStatus)
        case dismissActionError
    }
}
