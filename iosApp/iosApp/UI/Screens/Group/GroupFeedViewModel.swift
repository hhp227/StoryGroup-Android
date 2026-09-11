import Combine
import Foundation
import Shared

/// 소식 탭 — composeApp GroupFeedViewModel.kt와 1:1 미러(탭별 VM 분리, 레거시 탭 Fragment VM 구조).
/// 피드는 UiState에 담기는 최신 PagingData. 갱신은 VM이 스트림을 통째로 갈아끼우는 방식이다 —
/// Compose 쪽 상세는 NavHost 목적지라 글쓰기 복귀 때 화면이 새로 만들어지는데, 그 첫 프레임엔
/// 일회성 Event도 프레젠터 refresh()도 닿지 못한다. 트리거는 상태라서 타이밍과 무관하다.
final class GroupFeedViewModel: MviViewModel {
    typealias Event = Never

    @Published private(set) var uiState = UiState()

    // 스트림을 통째로 갈아끼우는 트리거 — 복귀 첫 프레임에 유실되지 않도록 이벤트가 아닌 상태다
    private let refreshTrigger = CurrentValueSubject<Int, Never>(0)

    let groupId: Int64

    // 목록 카드용 좋아요 토글 — 상세용 setPostLikedUseCase와 달리 좋아요 목록을 다시 읽지 않는다
    private let togglePostLikeUseCase: TogglePostLikeUseCase

    private var cancellables = Set<AnyCancellable>()

    private func setPagingData(_ pagingData: PagingData<Post>) {
        uiState.pagingData = pagingData
    }

    /// 수정/차단/삭제는 재조회 대신 현재 스냅샷에서 그 항목만 패치한다(Kotlin과 동일 —
    /// suspend 변환이라 Swift 클로저를 못 넘겨 PostBridges 브리지 함수 사용)
    private func applyPostUpdate(_ post: Post) {
        uiState.pagingData = PostBridgesKt.postPagingDataWithUpdate(pagingData: uiState.pagingData, post: post)
    }

    private func removeBlockedAuthorPosts(_ userId: Int64) {
        uiState.pagingData = PostBridgesKt.postPagingDataWithoutAuthor(pagingData: uiState.pagingData, userId: userId)
    }

    private func removeDeletedPost(_ postId: Int64) {
        uiState.pagingData = PostBridgesKt.postPagingDataWithoutPost(pagingData: uiState.pagingData, postId: postId)
    }

    func onAction(_ action: Action) {
        switch action {
        // 글쓰기 성공·당겨서 새로고침 — 새 PagingSource가 첫 페이지부터 다시 읽는다
        case .refresh: refreshTrigger.send(refreshTrigger.value + 1)
        case .toggleLike(let post): toggleLike(post)
        case .dismissLikeError: uiState.likeError = nil
        }
    }

    /// 성공 반영은 리포지토리의 postUpdates 알림(applyPostUpdate)이 담당 — 여기선 실패만 다룬다
    private func toggleLike(_ post: Post) {
        Task { @MainActor in
            do {
                try await togglePostLikeUseCase.invoke(groupId: post.groupId, postId: post.id, liked: !post.likedByMe)
            } catch {
                uiState.likeError = error.kotlinMessage(fallback: "좋아요 처리에 실패했습니다.")
            }
        }
    }

    init(
        groupId: Int64,
        getGroupPostsPagingDataUseCase: GetGroupPostsPagingDataUseCase = AppContainer.shared.getGroupPostsPagingDataUseCase,
        observePostUpdatesUseCase: ObservePostUpdatesUseCase = AppContainer.shared.observePostUpdatesUseCase,
        observeUserBlocksUseCase: ObserveUserBlocksUseCase = AppContainer.shared.observeUserBlocksUseCase,
        observePostDeletionsUseCase: ObservePostDeletionsUseCase = AppContainer.shared.observePostDeletionsUseCase,
        togglePostLikeUseCase: TogglePostLikeUseCase = AppContainer.shared.togglePostLikeUseCase
    ) {
        self.groupId = groupId
        self.togglePostLikeUseCase = togglePostLikeUseCase

        // UseCase는 cachedIn 없는 Flow를 반환하므로 프레젠테이션 경계인 여기서 캐시를 적용한다
        // (Kotlin: refreshTrigger.flatMapLatest { useCase(groupId) }.cachedIn(viewModelScope))
        refreshTrigger
            .map { _ in getGroupPostsPagingDataUseCase(groupId: groupId).cachedIn() }
            .switchToLatest()
            .sink { [weak self] in self?.setPagingData($0) }
            .store(in: &cancellables)
        KotlinFlowPublisher<Post> { onEach in
            observePostUpdatesUseCase.updatesFlow().subscribe(onEach: onEach)
        }
        .sink { [weak self] post in self?.applyPostUpdate(post) }
        .store(in: &cancellables)
        KotlinFlowPublisher<KotlinLong> { onEach in
            observeUserBlocksUseCase.blocksFlow().subscribe(onEach: onEach)
        }
        .sink { [weak self] userId in self?.removeBlockedAuthorPosts(userId.int64Value) }
        .store(in: &cancellables)
        KotlinFlowPublisher<KotlinLong> { onEach in
            observePostDeletionsUseCase.deletionsFlow().subscribe(onEach: onEach)
        }
        .sink { [weak self] postId in self?.removeDeletedPost(postId.int64Value) }
        .store(in: &cancellables)
    }

    struct UiState {
        var pagingData: PagingData<Post> = PostBridgesKt.emptyPostPagingData()
        // 카드 좋아요 실패 안내 — 서버 확정 방식이라 실패해도 되돌릴 UI 상태가 없다
        var likeError: String? = nil
    }

    enum Action {
        case refresh
        case toggleLike(Post)
        case dismissLikeError
    }
}
