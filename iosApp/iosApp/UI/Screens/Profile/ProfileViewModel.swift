import Foundation
import Shared

/// 내 정보(GET /api/users/me) — composeApp ProfileViewModel.kt와 1:1 미러. 프로필 화면/드로어 헤더가 공유한다.
final class ProfileViewModel: MviViewModel {
    typealias Event = Never

    @Published private(set) var uiState = UiState()

    private let getMyProfileUseCase: GetMyProfileUseCase

    func onAction(_ action: Action) {
        switch action {
        case .load: load()
        }
    }

    /// 로그인 직후 발화 — 재로그인 시에도 항상 새로 가져온다(이전 값은 로딩 중에도 유지)
    private func load() {
        if uiState.isLoading { return }

        uiState.isLoading = true
        uiState.error = nil
        Task { @MainActor in
            do {
                let profile = try await getMyProfileUseCase.invoke()
                uiState.isLoading = false
                uiState.profile = profile
            } catch {
                uiState.isLoading = false
                uiState.error = error.kotlinMessage(fallback: "내 정보를 불러오지 못했습니다.")
            }
        }
    }

    init(container: AppContainer) {
        getMyProfileUseCase = container.getMyProfileUseCase
    }

    struct UiState {
        var isLoading = false
        var profile: Profile? = nil
        var error: String? = nil
    }

    enum Action {
        case load
    }
}
