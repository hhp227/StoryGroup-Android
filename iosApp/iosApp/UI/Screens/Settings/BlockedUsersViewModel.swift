import Combine
import Foundation
import Shared

/// 차단 사용자 관리 — composeApp BlockedUsersViewModel.kt와 1:1 미러(웹 /settings/blocked).
/// 해제 성공은 목록에서 그 행만 제거한다(웹과 동일 — 재조회 없음). 화면 전환이 없어 Event=Never.
final class BlockedUsersViewModel: MviViewModel {
    typealias Event = Never

    @Published private(set) var uiState = UiState()

    private let getBlockedUsersUseCase: GetBlockedUsersUseCase

    private let unblockUserUseCase: UnblockUserUseCase

    func onAction(_ action: Action) {
        switch action {
        case .refresh: refresh()
        case .unblock(let userId): unblock(userId)
        case .dismissActionError: uiState.actionError = nil
        }
    }

    private func refresh() {
        if uiState.isLoading { return }

        uiState.isLoading = true
        uiState.loadError = nil
        Task { @MainActor in
            do {
                let blocked = try await getBlockedUsersUseCase.invoke()
                uiState.isLoading = false
                uiState.blocked = blocked
            } catch {
                uiState.isLoading = false
                uiState.loadError = error.kotlinMessage(fallback: "차단 목록을 불러오지 못했습니다.")
            }
        }
    }

    private func unblock(_ userId: Int64) {
        if uiState.busyUserId != nil { return }

        uiState.busyUserId = userId
        uiState.actionError = nil
        Task { @MainActor in
            do {
                try await unblockUserUseCase.invoke(userId: userId)
                uiState.busyUserId = nil
                uiState.blocked = uiState.blocked?.filter { $0.userId != userId }
            } catch {
                uiState.busyUserId = nil
                uiState.actionError = error.kotlinMessage(fallback: "차단 해제에 실패했습니다.")
            }
        }
    }

    init(getBlockedUsersUseCase: GetBlockedUsersUseCase = AppContainer.shared.getBlockedUsersUseCase, unblockUserUseCase: UnblockUserUseCase = AppContainer.shared.unblockUserUseCase) {
        self.getBlockedUsersUseCase = getBlockedUsersUseCase
        self.unblockUserUseCase = unblockUserUseCase
        refresh()
    }

    struct UiState {
        // nil=아직 로드 전 — 빈 목록(차단 없음)과 구분한다
        var blocked: [BlockedUser]? = nil
        var isLoading = false
        var loadError: String? = nil
        // 해제 진행 중인 행 — 그 행의 버튼만 잠근다(웹 busyFor 미러)
        var busyUserId: Int64? = nil
        var actionError: String? = nil
    }

    enum Action {
        case refresh
        case unblock(Int64)
        case dismissActionError
    }
}
