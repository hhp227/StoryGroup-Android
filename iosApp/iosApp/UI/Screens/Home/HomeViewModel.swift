import Combine
import Foundation
import Shared

/// 홈(라운지) 피드 — composeApp HomeViewModel.kt와 1:1 미러(Paging-CRUD 샘플 패턴).
/// 페이징(라운지 해석 포함)은 shared 데이터 계층 소유, VM은 캐시(cachedIn)와
/// 갱신 Event 발화만 담당하고 UiState에 최신 PagingData를 담는다.
/// 갱신은 화면이 Event를 받아 프레젠터 refresh()로 수행 — 같은 스트림이 새 세대를 방출하므로
/// 스트림 교체(트리거)가 없다.
final class HomeViewModel: MviViewModel {
    @Published private(set) var uiState = UiState()

    let event = PassthroughSubject<Event, Never>()

    private var cancellables = Set<AnyCancellable>()

    private func setPagingData(_ pagingData: PagingData<Post>) {
        uiState.pagingData = pagingData
    }

    func onAction(_ action: Action) {
        switch action {
        // 글쓰기 성공 시 발화 — 화면이 refresh()로 라운지를 다시 찾고 첫 페이지부터 다시 읽는다
        case .refresh:
            event.send(.refresh)
        }
    }

    init(getLoungePostsPagingDataUseCase: GetLoungePostsPagingDataUseCase) {
        // UseCase는 cachedIn 없는 Flow를 반환하므로 프레젠테이션 경계인 여기서 캐시를 적용한다
        // (Kotlin: getLoungePostsPagingDataUseCase().cachedIn(viewModelScope).onEach(::setPagingData).launchIn)
        getLoungePostsPagingDataUseCase()
            .cachedIn()
            .sink { [weak self] in self?.setPagingData($0) }
            .store(in: &cancellables)
    }

    /// 게시글 목록은 Paging 스트림의 최신 스냅샷 — 로딩/에러/추가 로드는 화면이 LoadState로 그린다
    struct UiState {
        // Kotlin의 PagingData.empty() 대응 — ObjC 제네릭 클래스에는 static 확장을 못 붙여 브리지 함수 직접 호출
        var pagingData: PagingData<Post> = PostBridgesKt.emptyPostPagingData()
    }

    enum Action {
        case refresh
    }

    enum Event {
        case refresh
    }
}
