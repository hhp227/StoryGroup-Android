import Foundation
import Shared

/// 홈(라운지) 피드 — composeApp HomeViewModel.kt와 1:1 미러.
/// 웹 메인 피드와 동일하게 내 그룹에서 라운지를 찾아 그 그룹의 게시글을 페이지 단위로 읽는다.
final class HomeViewModel: MviViewModel {
    typealias Event = Never

    @Published private(set) var uiState = UiState()

    private let getMyGroupsUseCase: GetMyGroupsUseCase
    private let getGroupPostsUseCase: GetGroupPostsUseCase

    private var loungeId: Int64? = nil
    private var nextPage: Int32 = 0

    private static let pageSize: Int32 = 20

    func onAction(_ action: Action) {
        switch action {
        case .refresh: refresh()
        case .loadMore: loadMore()
        }
    }

    /// 로그인 세션 진입 시 발화 — 라운지를 다시 찾고 첫 페이지부터 다시 읽는다(이전 목록은 로딩 중에도 유지)
    private func refresh() {
        if uiState.isLoading { return }

        uiState.isLoading = true
        uiState.error = nil
        Task { @MainActor in
            do {
                let groups = try await getMyGroupsUseCase.invoke()
                guard let lounge = groups.first(where: { $0.isLounge }) else {
                    uiState.isLoading = false
                    uiState.error = "라운지를 찾을 수 없습니다."
                    return
                }
                loungeId = lounge.id
                let posts = try await getGroupPostsUseCase.invoke(groupId: lounge.id, page: 0, size: Self.pageSize)
                nextPage = 1
                uiState.isLoading = false
                uiState.posts = posts
                uiState.hasMore = posts.count == Int(Self.pageSize)
            } catch {
                uiState.isLoading = false
                uiState.error = error.kotlinMessage(fallback: "피드를 불러오지 못했습니다.")
            }
        }
    }

    /// 목록 끝 도달 시 발화 — 다음 페이지를 이어 붙인다
    private func loadMore() {
        guard let groupId = loungeId else { return }
        if uiState.isLoading || uiState.isLoadingMore || !uiState.hasMore { return }

        uiState.isLoadingMore = true
        uiState.error = nil
        Task { @MainActor in
            do {
                let rows = try await getGroupPostsUseCase.invoke(groupId: groupId, page: nextPage, size: Self.pageSize)
                nextPage += 1
                // 새 글이 끼어들어 페이지 경계가 밀려도 중복 카드가 생기지 않게 id로 거른다(웹 미러)
                let seen = Set(uiState.posts.map { $0.id })
                uiState.isLoadingMore = false
                uiState.posts += rows.filter { !seen.contains($0.id) }
                uiState.hasMore = rows.count == Int(Self.pageSize)
            } catch {
                uiState.isLoadingMore = false
                uiState.error = error.kotlinMessage(fallback: "피드를 더 불러오지 못했습니다.")
            }
        }
    }

    init(container: AppContainer) {
        getMyGroupsUseCase = container.getMyGroupsUseCase
        getGroupPostsUseCase = container.getGroupPostsUseCase
    }

    struct UiState {
        var isLoading = false
        var isLoadingMore = false
        var posts: [Post] = []
        // 첫 로딩 전에는 false — 푸터(다음 페이지 트리거)가 미리 돌지 않게
        var hasMore = false
        var error: String? = nil
    }

    enum Action {
        case refresh
        case loadMore
    }
}
