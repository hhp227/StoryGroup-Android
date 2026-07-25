import Combine
import Foundation
import Shared

/// 채팅 허브 — composeApp ChatViewModel.kt와 1:1 미러.
/// 그룹 채팅방(GET /api/chat-rooms, 라운지 제외)+DM 방(GET /api/dm) 두 목록을 조회한다.
/// 서버 응답엔 마지막 메시지/미읽음 수가 없어 웹처럼 이름만 그린다.
/// (Kotlin은 두 목록을 async 병렬 조회하지만 KMP suspend는 메인 스레드 호출 제약이 있어 순차 await)
final class ChatViewModel: MviViewModel {
    typealias Event = Never

    @Published private(set) var uiState = UiState()

    private let getGroupChatRoomsUseCase: GetGroupChatRoomsUseCase

    private let getDirectRoomsUseCase: GetDirectRoomsUseCase

    func onAction(_ action: Action) {
        switch action {
        case .refresh: load()
        }
    }

    private func load() {
        if uiState.isLoading { return }

        uiState.isLoading = true
        uiState.error = nil
        Task { @MainActor in
            do {
                let groupRooms = try await getGroupChatRoomsUseCase.invoke()
                let directRooms = try await getDirectRoomsUseCase.invoke()
                uiState.isLoading = false
                uiState.groupRooms = groupRooms
                uiState.directRooms = directRooms
            } catch {
                uiState.isLoading = false
                uiState.error = error.kotlinMessage(fallback: "채팅방을 불러오지 못했습니다.")
            }
        }
    }

    init(
        getGroupChatRoomsUseCase: GetGroupChatRoomsUseCase,
        getDirectRoomsUseCase: GetDirectRoomsUseCase
    ) {
        self.getGroupChatRoomsUseCase = getGroupChatRoomsUseCase
        self.getDirectRoomsUseCase = getDirectRoomsUseCase
        load()
    }

    struct UiState {
        var groupRooms: [GroupChatRoom] = []
        var directRooms: [DirectRoom] = []
        var isLoading = false
        var error: String? = nil
    }

    enum Action {
        case refresh
    }
}
