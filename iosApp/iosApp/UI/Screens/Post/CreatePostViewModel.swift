import Combine
import Foundation
import Shared

/// 게시글 작성 — composeApp CreatePostViewModel.kt와 1:1 미러.
/// groupId가 nil이면 라운지(홈 피드)에 게시한다(웹 메인 피드 폼 미러).
/// 성공은 Event.created 일회성 발화 — 호출부가 피드 갱신+닫기를 처리한다.
final class CreatePostViewModel: MviViewModel {
    @Published private(set) var uiState = UiState()

    let event = PassthroughSubject<Event, Never>()

    private let groupId: Int64?

    private let createPostUseCase: CreatePostUseCase

    private let createLoungePostUseCase: CreateLoungePostUseCase

    func onAction(_ action: Action) {
        switch action {
        case .submit(let text): submit(text: text)
        case .clearError: uiState.error = nil
        }
    }

    private func submit(text: String) {
        if uiState.isLoading { return }
        // 첨부 없는 MVP라 본문 필수 — 백엔드의 "본문/첨부 중 하나는 필수" 규칙과 일치
        if text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            uiState.error = "내용을 입력해주세요."
            return
        }
        uiState.isLoading = true
        uiState.error = nil
        Task { @MainActor in
            do {
                if let groupId = groupId {
                    _ = try await createPostUseCase.invoke(groupId: groupId, text: text)
                } else {
                    _ = try await createLoungePostUseCase.invoke(text: text)
                }
                uiState.isLoading = false
                event.send(.created)
            } catch {
                uiState.isLoading = false
                uiState.error = error.kotlinMessage(fallback: "게시글 작성에 실패했습니다.")
            }
        }
    }

    init(container: AppContainer, groupId: Int64?) {
        self.groupId = groupId
        createPostUseCase = container.createPostUseCase
        createLoungePostUseCase = container.createLoungePostUseCase
    }

    struct UiState {
        var isLoading = false
        var error: String? = nil
    }

    enum Action {
        case submit(text: String)
        case clearError
    }

    enum Event {
        case created
    }
}
