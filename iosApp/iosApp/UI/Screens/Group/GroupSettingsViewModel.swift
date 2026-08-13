import Combine
import Foundation
import Shared
// SwiftUI.Group(뷰)과 도메인 모델 Group의 동명 충돌 — 이 파일의 Group은 도메인 모델로 고정
import class Shared.Group

/// 설정 탭 — composeApp GroupSettingsViewModel.kt와 1:1 미러: 레거시 SettingsFragment
/// (item_settings.xml) 미러의 메뉴 리스트(탭별 VM 분리). 진입 시 GetGroup으로 self-load해
/// 역할(OWNER 행 구성)·라운지 분기를 판정한다.
/// 수정 폼은 GroupEditViewModel(풀스크린)로 분리 — 여기엔 삭제/나가기만 남는다.
final class GroupSettingsViewModel: MviViewModel {
    @Published private(set) var uiState = UiState()

    let event = PassthroughSubject<Event, Never>()

    let groupId: Int64

    private let getGroupUseCase: GetGroupUseCase

    private let deleteGroupUseCase: DeleteGroupUseCase

    private let leaveGroupUseCase: LeaveGroupUseCase

    func onAction(_ action: Action) {
        switch action {
        case .refresh: refresh()
        case .delete: close { try await self.deleteGroupUseCase.invoke(groupId: self.groupId) }
        case .leave: close { try await self.leaveGroupUseCase.invoke(groupId: self.groupId) }
        case .dismissCloseError: uiState.closeError = nil
        }
    }

    /// 진입(init)·재시도·수정 화면 복귀 시 발화 — 역할·라운지 판정용 그룹을 읽는다
    private func refresh() {
        if uiState.isLoading { return }

        uiState.isLoading = true
        uiState.error = nil
        Task { @MainActor in
            do {
                let group = try await getGroupUseCase.invoke(groupId: groupId)
                uiState.isLoading = false
                uiState.group = group
            } catch {
                uiState.isLoading = false
                uiState.error = error.kotlinMessage(fallback: "그룹 정보를 불러오지 못했습니다.")
            }
        }
    }

    /// 삭제/나가기 공용 — 성공하면 이 화면 자체가 닫힌다(Closed → pop+목록 갱신)
    private func close(_ operation: @escaping () async throws -> Void) {
        if uiState.isClosing { return }

        uiState.isClosing = true
        uiState.closeError = nil
        Task { @MainActor in
            do {
                try await operation()
                uiState.isClosing = false
                event.send(.closed)
            } catch {
                // OWNER 나가기 거부("그룹 삭제를 이용하세요") 등 서버 문구를 그대로 보여준다
                uiState.isClosing = false
                uiState.closeError = error.kotlinMessage(fallback: "처리에 실패했습니다.")
            }
        }
    }

    init(
        groupId: Int64,
        getGroupUseCase: GetGroupUseCase,
        deleteGroupUseCase: DeleteGroupUseCase,
        leaveGroupUseCase: LeaveGroupUseCase
    ) {
        self.groupId = groupId
        self.getGroupUseCase = getGroupUseCase
        self.deleteGroupUseCase = deleteGroupUseCase
        self.leaveGroupUseCase = leaveGroupUseCase
        refresh()
    }

    struct UiState {
        // 로드 원본 — 역할(OWNER 행 구성)·라운지 분기의 기준
        var group: Group? = nil
        var isLoading = false
        var error: String? = nil
        // 삭제/나가기 진행 — 확인 다이얼로그 표시 여부는 화면 로컬 상태
        var isClosing = false
        var closeError: String? = nil

        var isOwner: Bool { group?.myRole == .owner }
        var isLounge: Bool { group?.isLounge == true }
    }

    enum Action {
        case refresh
        case delete
        case leave
        case dismissCloseError
    }

    enum Event {
        /// 삭제/나가기 성공 — 화면이 onGroupClosed로 pop+그룹 목록 갱신을 요청한다
        case closed
    }
}
