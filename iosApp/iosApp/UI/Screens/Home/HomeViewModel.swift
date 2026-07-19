import Combine
import Paging
import Foundation
import Shared

/// 홈(라운지) 피드 — composeApp HomeViewModel.kt와 1:1 미러(Paging-CRUD 샘플 패턴).
/// 페이징(라운지 해석 포함)은 shared 데이터 계층 소유, VM은 캐시(cachedIn)와
/// 세션 재진입 갱신만 담당하고 UiState에 최신 PagingData를 담는다.
final class HomeViewModel: MviViewModel {
    typealias Event = Never

    @Published private(set) var uiState = UiState()

    // 재로그인 시 스트림을 통째로 갈아끼우는 트리거 — 라운지 재해석은 새 PagingSource가 수행
    private let refreshTrigger = CurrentValueSubject<Int, Never>(0)

    private var cancellables = Set<AnyCancellable>()

    private func setPagingData(_ pagingData: PagingData<Post>) {
        uiState.pagingData = pagingData
    }

    func onAction(_ action: Action) {
        switch action {
        // 로그인 세션 진입 시 발화 — 라운지를 다시 찾고 첫 페이지부터 다시 읽는다
        case .refresh:
            refreshTrigger.send(refreshTrigger.value + 1)
        }
    }

    init(container: AppContainer) {
        let getLoungePostsPagingDataUseCase = container.getLoungePostsPagingDataUseCase

        // UseCase는 cachedIn 없는 Flow를 반환하므로 프레젠테이션 경계인 여기서 캐시를 적용한다
        // (Kotlin: refreshTrigger.flatMapLatest { useCase() }.cachedIn(viewModelScope))
        refreshTrigger
            .map { _ in getLoungePostsPagingDataUseCase().cachedIn() }
            .switchToLatest()
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
}
