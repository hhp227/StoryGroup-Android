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

    /// 갱신 — 이전 값은 로딩 중에도 유지
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

    init(getMyProfileUseCase: GetMyProfileUseCase) {
        self.getMyProfileUseCase = getMyProfileUseCase
        // 세션 수명(MainShellView)에 소유되어 "생성 = 세션 진입 1회" — 바로 로드한다(재로그인 시 재생성)
        load()
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
