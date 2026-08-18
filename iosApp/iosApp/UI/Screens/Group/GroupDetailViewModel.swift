import Combine
import Foundation
import Shared

/// 그룹 상세 화면 수준 VM — composeApp GroupDetailViewModel.kt(축소판)와 1:1 미러.
/// 커버(이름/설명/역할)+상단바 채팅 버튼용 기본 방 id만 담당 — 탭 상태는 탭별 VM 5개가 소유.
final class GroupDetailViewModel: MviViewModel {
    typealias Event = Never

    @Published private(set) var uiState = UiState()

    let groupId: Int64

    private let getGroupUseCase: GetGroupUseCase

    private let getGroupDefaultChatRoomUseCase: GetGroupDefaultChatRoomUseCase

    func onAction(_ action: Action) {
        switch action {
        case .refresh: refresh()
        }
    }

    /// 상세 진입 시 발화 — VM이 탭 전환에도 유지되므로 재진입 때도 최신화된다
    private func refresh() {
        if uiState.isLoading { return }

        uiState.isLoading = true
        uiState.error = nil
        Task { @MainActor in
            do {
                let group = try await getGroupUseCase.invoke(groupId: groupId)
                // 상단바 채팅 버튼용 기본 방 id — 실패해도 상세는 그린다(버튼만 숨는다)
                let defaultChatRoomId = ((try? await getGroupDefaultChatRoomUseCase.invoke(groupId: groupId)) ?? nil)?.int64Value
                uiState.isLoading = false
                uiState.group = group
                uiState.defaultChatRoomId = defaultChatRoomId
            } catch {
                uiState.isLoading = false
                uiState.error = error.kotlinMessage(fallback: "그룹을 불러오지 못했습니다.")
            }
        }
    }

    init(groupId: Int64, getGroupUseCase: GetGroupUseCase, getGroupDefaultChatRoomUseCase: GetGroupDefaultChatRoomUseCase) {
        self.groupId = groupId
        self.getGroupUseCase = getGroupUseCase
        self.getGroupDefaultChatRoomUseCase = getGroupDefaultChatRoomUseCase
    }

    struct UiState {
        var group: Group? = nil
        var defaultChatRoomId: Int64? = nil
        var isLoading = false
        var error: String? = nil

        // 초대코드 버튼·일정 삭제(모더레이터) 노출 조건 — 웹 lib/roles canModerate 미러(라운지 제외)
        var canModerate: Bool {
            guard let group = group else { return false }
            return !group.isLounge && group.myRole != .member
        }
    }

    enum Action {
        case refresh
    }
}
