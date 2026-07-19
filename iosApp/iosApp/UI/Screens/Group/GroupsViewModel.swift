import Foundation
import Shared

/// 그룹 탭 목록 — composeApp GroupsViewModel.kt와 1:1 미러(웹 /groups 내 그룹 탭, 라운지 제외)
final class GroupsViewModel: MviViewModel {
    typealias Event = Never

    @Published private(set) var uiState = UiState()

    private let getMyGroupsUseCase: GetMyGroupsUseCase

    func onAction(_ action: Action) {
        switch action {
        case .refresh: refresh()
        }
    }

    /// 로그인 세션 진입/재진입 시 발화 — 이전 목록은 로딩 중에도 유지
    private func refresh() {
        if uiState.isLoading { return }

        uiState.isLoading = true
        uiState.error = nil
        Task { @MainActor in
            do {
                let groups = try await getMyGroupsUseCase.invoke()
                uiState.isLoading = false
                // 라운지는 홈 탭이 담당 — 웹 내 그룹 목록과 동일하게 제외
                uiState.groups = groups.filter { !$0.isLounge }
            } catch {
                uiState.isLoading = false
                uiState.error = error.kotlinMessage(fallback: "그룹 목록을 불러오지 못했습니다.")
            }
        }
    }

    init(container: AppContainer) {
        getMyGroupsUseCase = container.getMyGroupsUseCase
    }

    struct UiState {
        var isLoading = false
        var groups: [Group] = []
        var error: String? = nil
    }

    enum Action {
        case refresh
    }
}
