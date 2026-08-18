import Combine
import Foundation
import Shared

/// 멤버 탭 — composeApp GroupMembersViewModel.kt와 1:1 미러(탭별 VM 분리).
/// 인박스는 모더레이터 전용 API지만 role 게이트 없이 항상 시도하고 403은 빈 목록으로
/// 흡수한다(초대코드 버튼 노출만 화면이 상세 VM의 canModerate로 게이트).
/// 멤버 탭=공개 프로필 진입(DM은 프로필의 버튼 몫)이라 화면 전환이 없어 Event=Never.
final class GroupMembersViewModel: MviViewModel {
    typealias Event = Never

    @Published private(set) var uiState = UiState()

    let groupId: Int64

    private let getGroupMembersUseCase: GetGroupMembersUseCase

    private let getJoinRequestsUseCase: GetJoinRequestsUseCase

    private let approveJoinRequestUseCase: ApproveJoinRequestUseCase

    private let rejectJoinRequestUseCase: RejectJoinRequestUseCase

    private let createGroupInviteUseCase: CreateGroupInviteUseCase

    private let getBlockedUsersUseCase: GetBlockedUsersUseCase

    func onAction(_ action: Action) {
        switch action {
        case .refresh: refresh()
        case .approveJoinRequest(let userId): approveJoinRequest(userId: userId)
        case .rejectJoinRequest(let userId): rejectJoinRequest(userId: userId)
        case .createInvite(let maxUses, let expiresInDays):
            createInvite(maxUses: maxUses, expiresInDays: expiresInDays)
        case .dismissInvite:
            uiState.createdInvite = nil
            uiState.inviteError = nil
        }
    }

    /// 화면 진입 onAppear가 발화 — 멤버+인박스+차단 목록 로드(재진입 신선화 겸용)
    private func refresh() {
        if uiState.isLoading { return }

        uiState.isLoading = true
        uiState.error = nil
        Task { @MainActor in
            do {
                let members = try await getGroupMembersUseCase.invoke(groupId: groupId)
                // 모더레이터 전용 API — 권한이 없으면 403이라 빈 목록으로 흡수(인박스만 안 그려진다)
                let joinRequests = (try? await getJoinRequestsUseCase.invoke(groupId: groupId)) ?? []
                // 서버는 멤버 목록에서 차단 사용자를 빼주지 않는다 — 그리드에서 직접 걸러낸다
                let blockedUserIds = Set(((try? await getBlockedUsersUseCase.invoke()) ?? []).map { $0.userId })
                uiState.isLoading = false
                uiState.members = members
                uiState.blockedUserIds = blockedUserIds
                uiState.joinRequests = joinRequests
            } catch {
                uiState.isLoading = false
                uiState.error = error.kotlinMessage(fallback: "멤버를 불러오지 못했습니다.")
            }
        }
    }

    /// 가입 신청 승인 — 성공 시 인박스에서 제거하고 새 멤버를 목록에 반영한다(웹 handleApprove 미러)
    private func approveJoinRequest(userId: Int64) {
        if uiState.processingRequestUserId != nil { return }

        uiState.processingRequestUserId = userId
        uiState.actionError = nil
        Task { @MainActor in
            do {
                try await approveJoinRequestUseCase.invoke(groupId: groupId, userId: userId)
                // 승인은 확정됐으므로 멤버 재조회 실패는 무시한다 — 다음 refresh가 따라잡는다
                if let members = try? await getGroupMembersUseCase.invoke(groupId: groupId) {
                    uiState.members = members
                }
                uiState.joinRequests.removeAll { $0.userId == userId }
                uiState.processingRequestUserId = nil
            } catch {
                uiState.processingRequestUserId = nil
                uiState.actionError = error.kotlinMessage(fallback: "가입 승인에 실패했습니다.")
            }
        }
    }

    /// 가입 신청 거절 — 성공 시 인박스에서만 제거한다(웹 handleReject 미러)
    private func rejectJoinRequest(userId: Int64) {
        if uiState.processingRequestUserId != nil { return }

        uiState.processingRequestUserId = userId
        uiState.actionError = nil
        Task { @MainActor in
            do {
                try await rejectJoinRequestUseCase.invoke(groupId: groupId, userId: userId)
                uiState.joinRequests.removeAll { $0.userId == userId }
                uiState.processingRequestUserId = nil
            } catch {
                uiState.processingRequestUserId = nil
                uiState.actionError = error.kotlinMessage(fallback: "가입 거절에 실패했습니다.")
            }
        }
    }

    /// 초대코드 생성(모더레이터 전용) — 성공 시 다이얼로그가 결과(코드) 뷰로 전환된다
    private func createInvite(maxUses: Int?, expiresInDays: Int?) {
        if uiState.isCreatingInvite { return }

        uiState.isCreatingInvite = true
        uiState.inviteError = nil
        Task { @MainActor in
            do {
                let invite = try await createGroupInviteUseCase.invoke(
                    groupId: groupId,
                    maxUses: maxUses.map { KotlinInt(int: Int32($0)) },
                    expiresInDays: expiresInDays.map { KotlinInt(int: Int32($0)) }
                )
                uiState.isCreatingInvite = false
                uiState.createdInvite = invite
            } catch {
                uiState.isCreatingInvite = false
                uiState.inviteError = error.kotlinMessage(fallback: "초대코드 생성에 실패했습니다.")
            }
        }
    }

    init(
        groupId: Int64,
        getGroupMembersUseCase: GetGroupMembersUseCase,
        getJoinRequestsUseCase: GetJoinRequestsUseCase,
        approveJoinRequestUseCase: ApproveJoinRequestUseCase,
        rejectJoinRequestUseCase: RejectJoinRequestUseCase,
        createGroupInviteUseCase: CreateGroupInviteUseCase,
        getBlockedUsersUseCase: GetBlockedUsersUseCase,
        getCurrentUserIdUseCase: GetCurrentUserIdUseCase
    ) {
        self.groupId = groupId
        self.getGroupMembersUseCase = getGroupMembersUseCase
        self.getJoinRequestsUseCase = getJoinRequestsUseCase
        self.approveJoinRequestUseCase = approveJoinRequestUseCase
        self.rejectJoinRequestUseCase = rejectJoinRequestUseCase
        self.createGroupInviteUseCase = createGroupInviteUseCase
        self.getBlockedUsersUseCase = getBlockedUsersUseCase
        uiState.myUserId = getCurrentUserIdUseCase.invoke()?.int64Value
    }

    struct UiState {
        // 멤버 그리드에서 본인을 구분(본인은 DM 대상이 아니다) — 세션이 있는 한 nil이 아니다
        var myUserId: Int64? = nil
        var members: [GroupMember] = []
        /// 내가 차단한 사용자 — 서버가 멤버 목록에선 걸러주지 않아 화면이 직접 뺀다
        var blockedUserIds: Set<Int64> = []
        // 모더레이터에게만 채워진다 — 일반 멤버는 403이 빈 목록으로 흡수돼 인박스가 안 그려진다
        var joinRequests: [GroupJoinRequest] = []
        // 승인/거절 버튼 로딩 표시용 — 동시에 하나만 처리(웹 busyFor 미러)
        var processingRequestUserId: Int64? = nil
        var isLoading = false
        var error: String? = nil
        var actionError: String? = nil
        var createdInvite: GroupInvite? = nil
        var isCreatingInvite = false
        var inviteError: String? = nil

        /// 멤버 그리드에 그릴 멤버 — 차단한 사용자는 뺀다(차단=내 화면에서 숨김)
        var visibleMembers: [GroupMember] { members.filter { !blockedUserIds.contains($0.userId) } }
    }

    enum Action {
        case refresh
        case approveJoinRequest(userId: Int64)
        case rejectJoinRequest(userId: Int64)
        case createInvite(maxUses: Int?, expiresInDays: Int?)
        case dismissInvite
    }
}
