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

    /// 수정된 게시글을 현재 스냅샷에서 그 항목만 갈아끼운다 — refresh를 태우면 첫 페이지부터
    /// 전체 재조회라 이미 쌓아둔 페이지와 스크롤 위치를 잃는다(수정은 목록 구조를 바꾸지 않는다).
    /// 다음 세대(새로고침·재진입)부턴 서버 값이 그대로 이긴다.
    /// (Kotlin: pagingData.map { ... } — transform이 suspend라 Swift 클로저를 못 넘겨 브리지 함수 사용)
    /// 차단한 작성자의 글을 현재 스냅샷에서 걷어낸다 — refresh를 태우면 첫 페이지부터 전체
    /// 재조회라 쌓아둔 페이지와 스크롤 위치를 잃는다. 다음 세대부턴 서버가 알아서 걸러준다.
    /// (Kotlin: pagingData.filter { ... } — predicate가 suspend라 Swift 클로저를 못 넘겨 브리지 사용)
    private func removeBlockedAuthorPosts(_ userId: Int64) {
        uiState.pagingData = PostBridgesKt.postPagingDataWithoutAuthor(pagingData: uiState.pagingData, userId: userId)
    }

    private func applyPostUpdate(_ post: Post) {
        uiState.pagingData = PostBridgesKt.postPagingDataWithUpdate(pagingData: uiState.pagingData, post: post)
    }

    func onAction(_ action: Action) {
        switch action {
        // 글쓰기 성공 시 발화 — 화면이 refresh()로 라운지를 다시 찾고 첫 페이지부터 다시 읽는다
        case .refresh:
            event.send(.refresh)
        }
    }

    init(
        getLoungePostsPagingDataUseCase: GetLoungePostsPagingDataUseCase,
        observePostUpdatesUseCase: ObservePostUpdatesUseCase,
        observeUserBlocksUseCase: ObserveUserBlocksUseCase
    ) {
        // UseCase는 cachedIn 없는 Flow를 반환하므로 프레젠테이션 경계인 여기서 캐시를 적용한다
        // (Kotlin: getLoungePostsPagingDataUseCase().cachedIn(viewModelScope).onEach(::setPagingData).launchIn)
        getLoungePostsPagingDataUseCase()
            .cachedIn()
            .sink { [weak self] in self?.setPagingData($0) }
            .store(in: &cancellables)
        // 상세 화면에서 수정하면 목록도 바뀐 본문을 보여야 한다 — 재조회 대신 그 항목만 교체
        KotlinFlowPublisher<Post> { onEach in
            observePostUpdatesUseCase.updatesFlow().subscribe(onEach: onEach)
        }
        .sink { [weak self] post in self?.applyPostUpdate(post) }
        .store(in: &cancellables)
        // 차단하면 그 사람의 글이 목록에서 사라져야 한다 — 재조회 대신 그 항목들만 제거
        KotlinFlowPublisher<KotlinLong> { onEach in
            observeUserBlocksUseCase.blocksFlow().subscribe(onEach: onEach)
        }
        .sink { [weak self] userId in self?.removeBlockedAuthorPosts(userId.int64Value) }
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
