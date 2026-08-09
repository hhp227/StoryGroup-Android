import Combine
import Foundation
import Shared

/// 그룹 상세 — composeApp GroupDetailViewModel.kt와 1:1 미러(Paging-CRUD 샘플 패턴).
/// 커버+멤버는 UiState 필드, 피드는 UiState에 담기는 최신 PagingData.
/// groupId만 받아 스스로 로드한다 — 목록이 페이징으로 바뀌어 스냅샷 lookup이 불가(로드 전 group은 nil).
/// 피드 갱신은 화면이 Event를 받아 프레젠터 refresh()로 수행한다(홈 피드와 동일 패턴).
/// 모더레이터(방장/부방장)에겐 승인 대기 가입 신청 인박스가 함께 로드된다(웹 GroupMemberList 미러).
/// 멤버 스트립에서 타인을 탭하면 1:1 DM을 연다(웹 GroupMemberList의 DM 액션 미러).
/// 상단바 채팅 버튼용 기본 채팅방 id도 함께 로드한다(레거시 group.xml action_chat·웹 커버 "채팅" 버튼 미러).
final class GroupDetailViewModel: MviViewModel {
    @Published private(set) var uiState = UiState()

    let event = PassthroughSubject<Event, Never>()

    let groupId: Int64

    private let getGroupUseCase: GetGroupUseCase

    private let getGroupMembersUseCase: GetGroupMembersUseCase

    private let getJoinRequestsUseCase: GetJoinRequestsUseCase

    private let approveJoinRequestUseCase: ApproveJoinRequestUseCase

    private let rejectJoinRequestUseCase: RejectJoinRequestUseCase

    private let createGroupInviteUseCase: CreateGroupInviteUseCase

    private let openDirectRoomUseCase: OpenDirectRoomUseCase

    private let getGroupDefaultChatRoomUseCase: GetGroupDefaultChatRoomUseCase

    private var cancellables = Set<AnyCancellable>()

    private func setPagingData(_ pagingData: PagingData<Post>) {
        uiState.pagingData = pagingData
    }

    /// 수정된 게시글을 현재 스냅샷에서 그 항목만 갈아끼운다 — refresh를 태우면 첫 페이지부터
    /// 전체 재조회라 이미 쌓아둔 페이지와 스크롤 위치를 잃는다(수정은 목록 구조를 바꾸지 않는다).
    /// 다음 세대(새로고침·재진입)부턴 서버 값이 그대로 이긴다.
    /// (Kotlin: pagingData.map { ... } — transform이 suspend라 Swift 클로저를 못 넘겨 브리지 함수 사용)
    private func applyPostUpdate(_ post: Post) {
        uiState.pagingData = PostBridgesKt.postPagingDataWithUpdate(pagingData: uiState.pagingData, post: post)
    }

    func onAction(_ action: Action) {
        switch action {
        case .refresh: refresh()
        // 글쓰기 성공 시 발화 — 화면이 refresh()로 피드를 첫 페이지부터 다시 읽는다
        case .refreshFeed: event.send(.refreshFeed)
        case .approveJoinRequest(let userId): approveJoinRequest(userId: userId)
        case .rejectJoinRequest(let userId): rejectJoinRequest(userId: userId)
        case .createInvite(let maxUses, let expiresInDays):
            createInvite(maxUses: maxUses, expiresInDays: expiresInDays)
        case .dismissInvite:
            uiState.createdInvite = nil
            uiState.inviteError = nil
        case .openDm(let userId, let userName): openDm(userId: userId, userName: userName)
        case .dismissDm: uiState.dmError = nil
        }
    }

    /// 상세 진입 시 발화 — 그룹+멤버(+모더레이터면 가입 신청) 로드(피드는 Pager가 자체 로드/재시도)
    private func refresh() {
        if uiState.isLoading { return }

        uiState.isLoading = true
        uiState.error = nil
        Task { @MainActor in
            do {
                let group = try await getGroupUseCase.invoke(groupId: groupId)
                let members = try await getGroupMembersUseCase.invoke(groupId: groupId)
                // 가입 신청 목록은 모더레이터 전용 API — 권한이 있을 때만 조회하고,
                // 실패해도 상세 자체는 그린다(웹 GroupMemberList 미러, 라운지는 가입 신청 자체가 없다)
                let canModerate = !group.isLounge && group.myRole != .member
                let joinRequests = canModerate
                    ? ((try? await getJoinRequestsUseCase.invoke(groupId: groupId)) ?? [])
                    : []
                // 상단바 채팅 버튼용 기본 방 id — 실패해도 상세는 그린다(버튼만 숨고 다음 refresh가 따라잡는다)
                let defaultChatRoomId = ((try? await getGroupDefaultChatRoomUseCase.invoke(groupId: groupId)) ?? nil)?.int64Value
                uiState.isLoading = false
                uiState.group = group
                uiState.members = members
                uiState.joinRequests = joinRequests
                uiState.defaultChatRoomId = defaultChatRoomId
            } catch {
                uiState.isLoading = false
                uiState.error = error.kotlinMessage(fallback: "그룹을 불러오지 못했습니다.")
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

    /// 멤버와 1:1 DM 열기 — get-or-create(멱등)라 이미 방이 있으면 그 방으로 간다(웹 handleDm 미러)
    private func openDm(userId: Int64, userName: String) {
        if uiState.isOpeningDm { return }

        uiState.isOpeningDm = true
        uiState.dmError = nil
        Task { @MainActor in
            do {
                let chatRoomId = try await openDirectRoomUseCase.invoke(otherUserId: userId)
                uiState.isOpeningDm = false
                // 방 이름은 서버가 "DM" 고정이라 상대 이름을 제목으로 넘긴다(허브와 동일)
                event.send(.dmOpened(chatRoomId: chatRoomId.int64Value, title: userName))
            } catch {
                // 차단 관계(403 BLOCKED) 등 — 다이얼로그 안에 표시된다
                uiState.isOpeningDm = false
                uiState.dmError = error.kotlinMessage(fallback: "DM을 열지 못했습니다.")
            }
        }
    }

    init(
        groupId: Int64,
        getGroupUseCase: GetGroupUseCase,
        getGroupMembersUseCase: GetGroupMembersUseCase,
        getJoinRequestsUseCase: GetJoinRequestsUseCase,
        approveJoinRequestUseCase: ApproveJoinRequestUseCase,
        rejectJoinRequestUseCase: RejectJoinRequestUseCase,
        createGroupInviteUseCase: CreateGroupInviteUseCase,
        openDirectRoomUseCase: OpenDirectRoomUseCase,
        getGroupDefaultChatRoomUseCase: GetGroupDefaultChatRoomUseCase,
        getCurrentUserIdUseCase: GetCurrentUserIdUseCase,
        getGroupPostsPagingDataUseCase: GetGroupPostsPagingDataUseCase,
        observePostUpdatesUseCase: ObservePostUpdatesUseCase
    ) {
        self.groupId = groupId
        self.getGroupUseCase = getGroupUseCase
        self.getGroupMembersUseCase = getGroupMembersUseCase
        self.getJoinRequestsUseCase = getJoinRequestsUseCase
        self.approveJoinRequestUseCase = approveJoinRequestUseCase
        self.rejectJoinRequestUseCase = rejectJoinRequestUseCase
        self.createGroupInviteUseCase = createGroupInviteUseCase
        self.openDirectRoomUseCase = openDirectRoomUseCase
        self.getGroupDefaultChatRoomUseCase = getGroupDefaultChatRoomUseCase
        uiState.myUserId = getCurrentUserIdUseCase.invoke()?.int64Value

        // UseCase는 cachedIn 없는 Flow를 반환하므로 프레젠테이션 경계인 여기서 캐시를 적용한다
        // (Kotlin: useCase(groupId).cachedIn(viewModelScope).onEach(::setPagingData).launchIn)
        getGroupPostsPagingDataUseCase(groupId: groupId)
            .cachedIn()
            .sink { [weak self] in self?.setPagingData($0) }
            .store(in: &cancellables)
        // 상세 화면에서 수정하면 목록도 바뀐 본문을 보여야 한다 — 재조회 대신 그 항목만 교체
        KotlinFlowPublisher<Post> { onEach in
            observePostUpdatesUseCase.updatesFlow().subscribe(onEach: onEach)
        }
        .sink { [weak self] post in self?.applyPostUpdate(post) }
        .store(in: &cancellables)
    }

    struct UiState {
        // 로드 전 nil — 화면은 그룹 정보 자리만 비워 두고 커버/피드를 먼저 그린다
        var group: Group? = nil
        // 상단바 채팅 버튼이 여는 기본 채팅방(가장 먼저 생성된 방) — 로드 전/실패 시 nil이면 버튼이 숨는다
        var defaultChatRoomId: Int64? = nil
        // Kotlin의 PagingData.empty() 대응 — ObjC 제네릭 클래스에는 static 확장을 못 붙여 브리지 함수 직접 호출
        var pagingData: PagingData<Post> = PostBridgesKt.emptyPostPagingData()
        var members: [GroupMember] = []
        // 모더레이터에게만 채워진다 — 일반 멤버는 항상 빈 목록이라 인박스가 그려지지 않는다
        var joinRequests: [GroupJoinRequest] = []
        // 승인/거절 버튼 로딩 표시용 — 동시에 하나만 처리(웹 busyFor 미러)
        var processingRequestUserId: Int64? = nil
        var isLoading = false
        var error: String? = nil
        // 승인/거절 실패 문구 — 로드 에러(error)와 달리 상세 화면을 대체하지 않는다
        var actionError: String? = nil
        // 초대코드 다이얼로그 전용 — 생성 성공 시 createdInvite가 채워져 결과 뷰로 전환된다
        var createdInvite: GroupInvite? = nil
        var isCreatingInvite = false
        var inviteError: String? = nil
        // 멤버 스트립에서 본인을 구분(본인은 DM 대상이 아니다) — 세션이 있는 한 nil이 아니다
        var myUserId: Int64? = nil
        // DM 확인 다이얼로그 전용 — 실패 문구(차단 관계 등)는 다이얼로그 안에 표시된다
        var isOpeningDm = false
        var dmError: String? = nil

        // 초대코드 만들기 버튼 노출 조건 — 인박스와 동일한 모더레이터 판정(라운지 제외)
        var canModerate: Bool {
            guard let group = group else { return false }
            return !group.isLounge && group.myRole != .member
        }
    }

    enum Action {
        case refresh
        case refreshFeed
        case approveJoinRequest(userId: Int64)
        case rejectJoinRequest(userId: Int64)
        case createInvite(maxUses: Int?, expiresInDays: Int?)
        case dismissInvite
        case openDm(userId: Int64, userName: String)
        case dismissDm
    }

    enum Event {
        case refreshFeed
        /// DM 방 확보 성공 — 화면이 채팅방(groupId=nil)으로 push한다
        case dmOpened(chatRoomId: Int64, title: String)
    }
}
