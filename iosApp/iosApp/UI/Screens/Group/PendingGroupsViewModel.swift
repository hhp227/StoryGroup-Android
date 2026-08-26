import Combine
import Foundation
import Shared

/// 가입 신청중 화면 — composeApp PendingGroupsViewModel.kt와 1:1 미러(레거시 JoinRequestGroupFragment).
/// 방문마다 새로 생성(진입 시 init 로드 — DiscoverGroups와 동일)라 신청/취소 후 재조회 신호가 필요 없다.
final class PendingGroupsViewModel: MviViewModel {
    typealias Event = Never

    @Published private(set) var uiState = UiState()

    private let getMyJoinRequestedGroupsUseCase: GetMyJoinRequestedGroupsUseCase

    private let cancelJoinRequestUseCase: CancelJoinRequestUseCase

    func onAction(_ action: Action) {
        switch action {
        case .refresh:
            load()
        case .cancelRequest(let groupId):
            cancelRequest(groupId: groupId)
        }
    }

    private func load() {
        uiState.isLoading = true
        uiState.loadError = nil
        Task { @MainActor in
            do {
                let groups = try await getMyJoinRequestedGroupsUseCase.invoke()
                uiState.isLoading = false
                uiState.groups = groups
            } catch {
                uiState.isLoading = false
                uiState.loadError = error.kotlinMessage(fallback: "가입 신청중 그룹을 불러오지 못했습니다.")
            }
        }
    }

    private func cancelRequest(groupId: Int64) {
        if uiState.cancelingGroupId != nil { return }

        uiState.cancelingGroupId = groupId
        uiState.cancelError = nil
        Task { @MainActor in
            do {
                try await cancelJoinRequestUseCase.invoke(groupId: groupId)
                // 서버 재조회 없이 낙관적으로 제거 — 실패했더라도 다음 refresh 때 서버 상태로 수렴한다
                uiState.cancelingGroupId = nil
                uiState.groups.removeAll { $0.id == groupId }
            } catch {
                uiState.cancelingGroupId = nil
                uiState.cancelError = error.kotlinMessage(fallback: "신청 취소에 실패했습니다.")
            }
        }
    }

    init(
        getMyJoinRequestedGroupsUseCase: GetMyJoinRequestedGroupsUseCase = AppContainer.shared.getMyJoinRequestedGroupsUseCase,
        cancelJoinRequestUseCase: CancelJoinRequestUseCase = AppContainer.shared.cancelJoinRequestUseCase
    ) {
        self.getMyJoinRequestedGroupsUseCase = getMyJoinRequestedGroupsUseCase
        self.cancelJoinRequestUseCase = cancelJoinRequestUseCase
        load()
    }

    /// 목록은 탐색과 같은 모양(DiscoverGroup, membership=PENDING).
    /// 독립 화면이므로 로드 실패(loadError)는 재시도 상태로, 취소 실패(cancelError)는 목록 위 한 줄로 나눠 그린다
    struct UiState {
        var groups: [DiscoverGroup] = []
        var isLoading = false
        var loadError: String? = nil
        var cancelError: String? = nil
        // 신청 취소 버튼 로딩 표시용 — 동시에 하나만 처리
        var cancelingGroupId: Int64? = nil
    }

    enum Action {
        case refresh
        case cancelRequest(groupId: Int64)
    }
}
