import Combine
import Foundation
import Shared

/// 그룹 탭 목록 — composeApp GroupsViewModel.kt와 1:1 미러(Paging-CRUD 샘플 패턴).
/// 레거시 user_groups 페이징 미러(라운지 제외는 shared 데이터 계층).
/// GroupsView(keep-alive ZStack) 소유라 "생성 = 세션 진입 1회" — 초기값으로 즉시 시작한다.
final class GroupsViewModel: MviViewModel {
    typealias Event = Never

    @Published private(set) var uiState = UiState()

    // 스트림을 통째로 갈아끼우는 트리거 — 그룹 생성/가입 후 갱신용
    private let refreshTrigger = CurrentValueSubject<Int, Never>(0)

    private var cancellables = Set<AnyCancellable>()

    private func setPagingData(_ pagingData: PagingData<Group>) {
        uiState.pagingData = pagingData
    }

    func onAction(_ action: Action) {
        switch action {
        case .refresh:
            refreshTrigger.send(refreshTrigger.value + 1)
        }
    }

    init(container: AppContainer) {
        let getMyGroupsPagingDataUseCase = container.getMyGroupsPagingDataUseCase

        // UseCase는 cachedIn 없는 Flow를 반환하므로 프레젠테이션 경계인 여기서 캐시를 적용한다
        // (Kotlin: refreshTrigger.flatMapLatest { useCase() }.cachedIn(viewModelScope))
        refreshTrigger
            .map { _ in getMyGroupsPagingDataUseCase().cachedIn() }
            .switchToLatest()
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
}
