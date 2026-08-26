import Combine
import Foundation
import Shared

/// 그룹 탭 목록 — composeApp GroupsViewModel.kt와 1:1 미러(Paging-CRUD 샘플 패턴).
/// 레거시 user_groups 페이징 미러(라운지 제외는 shared 데이터 계층).
/// VM은 캐시(cachedIn)와 갱신 Event 발화만 담당 — 갱신은 화면이 Event를 받아 프레젠터
/// refresh()로 수행한다(홈 피드와 동일 패턴, 스트림 교체 없음).
/// 가입 신청중(PENDING) 그룹은 독립 화면(PendingGroupsView)으로 분리됐다 — 레거시 3분할 미러.
/// GroupsView(keep-alive ZStack) 소유라 "생성 = 세션 진입 1회" — init에서 바로 시작한다.
final class GroupsViewModel: MviViewModel {
    @Published private(set) var uiState = UiState()

    let event = PassthroughSubject<Event, Never>()

    private var cancellables = Set<AnyCancellable>()

    private func setPagingData(_ pagingData: PagingData<Group>) {
        uiState.pagingData = pagingData
    }

    func onAction(_ action: Action) {
        switch action {
        // 그룹 생성/가입 후 갱신용 — 화면이 refresh()로 목록을 첫 페이지부터 다시 읽는다
        case .refresh:
            event.send(.refresh)
        }
    }

    init(getMyGroupsPagingDataUseCase: GetMyGroupsPagingDataUseCase = AppContainer.shared.getMyGroupsPagingDataUseCase) {
        // UseCase는 cachedIn 없는 Flow를 반환하므로 프레젠테이션 경계인 여기서 캐시를 적용한다
        // (Kotlin: getMyGroupsPagingDataUseCase().cachedIn(viewModelScope).onEach(::setPagingData).launchIn)
        getMyGroupsPagingDataUseCase()
            .cachedIn()
            .sink { [weak self] in self?.setPagingData($0) }
            .store(in: &cancellables)
    }

    /// 그룹 목록은 Paging 스트림의 최신 스냅샷 — 로딩/에러/추가 로드는 화면이 LoadState로 그린다
    struct UiState {
        // Kotlin의 PagingData.empty() 대응 — ObjC 제네릭 클래스에는 static 확장을 못 붙여 브리지 함수 직접 호출
        var pagingData: PagingData<Group> = GroupBridgesKt.emptyGroupPagingData()
    }

    enum Action {
        case refresh
    }

    enum Event {
        case refresh
    }
}
