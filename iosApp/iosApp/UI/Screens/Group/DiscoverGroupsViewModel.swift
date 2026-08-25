import Combine
import Foundation
import Shared

/// 그룹 찾기 — composeApp DiscoverGroupsViewModel.kt와 1:1 미러.
/// 검색어/정렬이 바뀔 때만 새 Pager를 구독하는 표준 Paging 검색 패턴(map+switchToLatest,
/// Kotlin flatMapLatest 대응). 가입/신청 직후엔 전체 목록을 새로고침하지 않고 localOverrides로
/// 카드·다이얼로그 상태만 낙관적으로 갱신해 스크롤 위치를 보존한다. 가입 완료(JOINED) 시
/// Event.joined를 발화해 화면이 세션 GroupsViewModel을 갱신하게 한다(내 그룹 탭 반영).
final class DiscoverGroupsViewModel: MviViewModel {
    @Published private(set) var uiState = UiState()

    let event = PassthroughSubject<Event, Never>()

    private var cancellables = Set<AnyCancellable>()

    private let joinGroupUseCase: JoinGroupUseCase

    private let joinGroupByCodeUseCase: JoinGroupByCodeUseCase

    private let cancelJoinRequestUseCase: CancelJoinRequestUseCase

    private func setPagingData(_ pagingData: PagingData<DiscoverGroup>) {
        uiState.pagingData = pagingData
    }

    func onAction(_ action: Action) {
        switch action {
        case .search(let query): uiState.query = query
        case .changeSort(let sort): uiState.sort = sort
        case .join(let groupId): join(groupId: groupId)
        case .joinByCode(let code): joinByCode(code: code)
        case .dismissJoinByCodeError: uiState.joinByCodeError = nil
        case .cancelRequest(let groupId): cancelRequest(groupId: groupId)
        }
    }

    private func join(groupId: Int64) {
        if uiState.joiningGroupId != nil { return }

        uiState.joiningGroupId = groupId
        uiState.error = nil
        Task { @MainActor in
            do {
                let result = try await joinGroupUseCase.invoke(groupId: groupId)
                let status: GroupMembershipStatus = result.status == .joined ? .member : .pending
                uiState.joiningGroupId = nil
                uiState.localOverrides[groupId] = status
                // 즉시 가입만 내 그룹 목록 갱신 신호 — 승인제 신청은 신청중 화면이 진입 시 스스로 로드한다
                if result.status == .joined { event.send(.joined) }
            } catch {
                uiState.joiningGroupId = nil
                uiState.error = error.kotlinMessage(fallback: "가입에 실패했습니다.")
            }
        }
    }

    /// 초대코드 가입 — 승인제와 무관하게 즉시 MEMBER, 목록에 있으면 카드 상태도 낙관 갱신한다
    private func joinByCode(code: String) {
        let trimmed = code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()

        if trimmed.isEmpty || uiState.isJoiningByCode { return }

        uiState.isJoiningByCode = true
        uiState.joinByCodeError = nil
        Task { @MainActor in
            do {
                let group = try await joinGroupByCodeUseCase.invoke(code: trimmed)
                uiState.isJoiningByCode = false
                uiState.localOverrides[group.id] = .member
                event.send(.joinedByCode)
            } catch {
                uiState.isJoiningByCode = false
                uiState.joinByCodeError = error.kotlinMessage(fallback: "초대 코드 가입에 실패했습니다.")
            }
        }
    }

    private func cancelRequest(groupId: Int64) {
        if uiState.joiningGroupId != nil { return }

        uiState.joiningGroupId = groupId
        uiState.error = nil
        Task { @MainActor in
            do {
                try await cancelJoinRequestUseCase.invoke(groupId: groupId)
                uiState.joiningGroupId = nil
                uiState.localOverrides[groupId] = .none
            } catch {
                uiState.joiningGroupId = nil
                uiState.error = error.kotlinMessage(fallback: "신청 취소에 실패했습니다.")
            }
        }
    }

    init(
        getDiscoverGroupsPagingDataUseCase: GetDiscoverGroupsPagingDataUseCase,
        joinGroupUseCase: JoinGroupUseCase,
        joinGroupByCodeUseCase: JoinGroupByCodeUseCase,
        cancelJoinRequestUseCase: CancelJoinRequestUseCase
    ) {
        self.joinGroupUseCase = joinGroupUseCase
        self.joinGroupByCodeUseCase = joinGroupByCodeUseCase
        self.cancelJoinRequestUseCase = cancelJoinRequestUseCase

        $uiState
            .map { ($0.query, $0.sort) }
            .removeDuplicates { $0 == $1 }
            .map { query, sort in getDiscoverGroupsPagingDataUseCase(query: query, sort: sort).cachedIn() }
            .switchToLatest()
            .sink { [weak self] in self?.setPagingData($0) }
            .store(in: &cancellables)
    }

    struct UiState {
        // Kotlin의 PagingData.empty() 대응 — ObjC 제네릭 클래스에는 static 확장을 못 붙여 브리지 함수 직접 호출
        var pagingData: PagingData<DiscoverGroup> = GroupBridgesKt.emptyDiscoverGroupPagingData()
        var query: String = ""
        var sort: DiscoverSort = .recent
        // 다이얼로그 버튼 로딩 표시용 — 동시에 하나만 처리
        var joiningGroupId: Int64? = nil
        // 가입/신청/취소 직후 서버 재조회 없이 카드·다이얼로그 상태를 낙관적으로 덮어쓴다
        var localOverrides: [Int64: GroupMembershipStatus] = [:]
        var error: String? = nil
        // 초대코드 다이얼로그 전용 — 목록 에러(error)와 분리해 다이얼로그 안에서만 그린다
        var isJoiningByCode = false
        var joinByCodeError: String? = nil

        func membership(of group: DiscoverGroup) -> GroupMembershipStatus {
            localOverrides[group.id] ?? group.membership
        }
    }

    enum Action {
        case search(query: String)
        case changeSort(sort: DiscoverSort)
        case join(groupId: Int64)
        case joinByCode(code: String)
        case dismissJoinByCodeError
        case cancelRequest(groupId: Int64)
    }

    enum Event {
        /// 자동 승인으로 즉시 가입 완료 — 화면이 세션 GroupsViewModel을 갱신한다
        case joined

        /// 초대코드로 가입 완료 — 화면이 다이얼로그를 닫고 세션 GroupsViewModel을 갱신한다
        case joinedByCode
    }
}
