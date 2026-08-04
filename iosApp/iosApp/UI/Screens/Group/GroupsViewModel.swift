import Combine
import Foundation
import Shared

/// 그룹 탭 목록 — composeApp GroupsViewModel.kt와 1:1 미러(Paging-CRUD 샘플 패턴).
/// 레거시 user_groups 페이징 미러(라운지 제외는 shared 데이터 계층).
/// VM은 캐시(cachedIn)와 갱신 Event 발화만 담당 — 갱신은 화면이 Event를 받아 프레젠터
/// refresh()로 수행한다(홈 피드와 동일 패턴, 스트림 교체 없음).
/// 가입 신청중(PENDING) 그룹은 페이징과 별개의 소수 목록이라 일반 상태로 들고,
/// 진입/refresh/신청·취소 신호(refreshPending) 때 재조회한다.
/// GroupsView(keep-alive ZStack) 소유라 "생성 = 세션 진입 1회" — init에서 바로 시작한다.
final class GroupsViewModel: MviViewModel {
    @Published private(set) var uiState = UiState()

    let event = PassthroughSubject<Event, Never>()

    private var cancellables = Set<AnyCancellable>()

    private let getMyJoinRequestedGroupsUseCase: GetMyJoinRequestedGroupsUseCase

    private let cancelJoinRequestUseCase: CancelJoinRequestUseCase

    private func setPagingData(_ pagingData: PagingData<Group>) {
        uiState.pagingData = pagingData
    }

    func onAction(_ action: Action) {
        switch action {
        // 그룹 생성/가입 후 갱신용 — 화면이 refresh()로 목록을 첫 페이지부터 다시 읽는다
        case .refresh:
            event.send(.refresh)
            loadPendingGroups()
        // 승인제 신청/신청 취소는 내 그룹 목록엔 영향이 없다 — 신청중 섹션만 재조회
        case .refreshPending:
            loadPendingGroups()
        case .cancelRequest(let groupId):
            cancelRequest(groupId: groupId)
        }
    }

    private func loadPendingGroups() {
        Task { @MainActor in
            // 실패는 조용히 넘긴다 — 신청중 섹션은 부가 정보라 그룹 목록(페이징)까지 막지 않는다
            guard let groups = try? await getMyJoinRequestedGroupsUseCase.invoke() else { return }
            uiState.pendingGroups = groups
            uiState.pendingError = nil
        }
    }

    private func cancelRequest(groupId: Int64) {
        if uiState.cancelingGroupId != nil { return }

        uiState.cancelingGroupId = groupId
        uiState.pendingError = nil
        Task { @MainActor in
            do {
                try await cancelJoinRequestUseCase.invoke(groupId: groupId)
                // 서버 재조회 없이 낙관적으로 제거 — 실패했더라도 다음 refresh 때 서버 상태로 수렴한다
                uiState.cancelingGroupId = nil
                uiState.pendingGroups.removeAll { $0.id == groupId }
            } catch {
                uiState.cancelingGroupId = nil
                uiState.pendingError = error.kotlinMessage(fallback: "신청 취소에 실패했습니다.")
            }
        }
    }

    init(
        getMyGroupsPagingDataUseCase: GetMyGroupsPagingDataUseCase,
        getMyJoinRequestedGroupsUseCase: GetMyJoinRequestedGroupsUseCase,
        cancelJoinRequestUseCase: CancelJoinRequestUseCase
    ) {
        self.getMyJoinRequestedGroupsUseCase = getMyJoinRequestedGroupsUseCase
        self.cancelJoinRequestUseCase = cancelJoinRequestUseCase

        // UseCase는 cachedIn 없는 Flow를 반환하므로 프레젠테이션 경계인 여기서 캐시를 적용한다
        // (Kotlin: getMyGroupsPagingDataUseCase().cachedIn(viewModelScope).onEach(::setPagingData).launchIn)
        getMyGroupsPagingDataUseCase()
            .cachedIn()
            .sink { [weak self] in self?.setPagingData($0) }
            .store(in: &cancellables)
        loadPendingGroups()
    }

    /// 그룹 목록은 Paging 스트림의 최신 스냅샷 — 로딩/에러/추가 로드는 화면이 LoadState로 그린다.
    /// 가입 신청중 목록은 탐색과 같은 모양(DiscoverGroup, membership=PENDING) — 비어 있으면 화면이 섹션을 숨긴다
    struct UiState {
        // Kotlin의 PagingData.empty() 대응 — ObjC 제네릭 클래스에는 static 확장을 못 붙여 브리지 함수 직접 호출
        var pagingData: PagingData<Group> = GroupBridgesKt.emptyGroupPagingData()
        var pendingGroups: [DiscoverGroup] = []
        // 신청 취소 버튼 로딩 표시용 — 동시에 하나만 처리
        var cancelingGroupId: Int64? = nil
        // 신청 취소 실패 문구 — 신청중 섹션 안에서만 그린다
        var pendingError: String? = nil
    }

    enum Action {
        case refresh
        case refreshPending
        case cancelRequest(groupId: Int64)
    }

    enum Event {
        case refresh
    }
}
