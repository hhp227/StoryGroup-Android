import Foundation
import Shared

/// 그룹 상세 — composeApp GroupDetailViewModel.kt와 1:1 미러.
/// 목록에서 받은 그룹으로 즉시 그리고, refresh에서 단건 조회로 신선화한다.
final class GroupDetailViewModel: MviViewModel {
    typealias Event = Never

    @Published private(set) var uiState: UiState

    private let getGroupUseCase: GetGroupUseCase
    private let getGroupMembersUseCase: GetGroupMembersUseCase
    private let getGroupPostsUseCase: GetGroupPostsUseCase

    private var nextPage: Int32 = 0

    private static let pageSize: Int32 = 20

    func onAction(_ action: Action) {
        switch action {
        case .refresh: refresh()
        case .loadMore: loadMore()
        }
    }

    /// 상세 진입 시 발화 — 그룹 신선화+멤버+첫 페이지(이전 내용은 로딩 중에도 유지)
    private func refresh() {
        if uiState.isLoading { return }

        uiState.isLoading = true
        uiState.error = nil
        Task { @MainActor in
            do {
                let groupId = uiState.group.id
                let group = try await getGroupUseCase.invoke(groupId: groupId)
                let members = try await getGroupMembersUseCase.invoke(groupId: groupId)
                let posts = try await getGroupPostsUseCase.invoke(groupId: groupId, page: 0, size: Self.pageSize)
                nextPage = 1
                uiState.isLoading = false
                uiState.group = group
                uiState.members = members
                uiState.posts = posts
                uiState.hasMore = posts.count == Int(Self.pageSize)
            } catch {
                uiState.isLoading = false
                uiState.error = error.kotlinMessage(fallback: "그룹을 불러오지 못했습니다.")
            }
        }
    }

    /// 목록 끝 도달 시 발화 — 다음 페이지를 이어 붙인다(HomeViewModel 미러)
    private func loadMore() {
        if uiState.isLoading || uiState.isLoadingMore || !uiState.hasMore { return }

        uiState.isLoadingMore = true
        uiState.error = nil
        Task { @MainActor in
            do {
                let rows = try await getGroupPostsUseCase.invoke(
                    groupId: uiState.group.id,
                    page: nextPage,
                    size: Self.pageSize
                )
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

    init(container: AppContainer, group: Group) {
        getGroupUseCase = container.getGroupUseCase
        getGroupMembersUseCase = container.getGroupMembersUseCase
        getGroupPostsUseCase = container.getGroupPostsUseCase
        uiState = UiState(group: group)
    }

    struct UiState {
        var group: Group
        var members: [GroupMember] = []
        var posts: [Post] = []
        var isLoading = false
        var isLoadingMore = false
        // 첫 로딩 전에는 false — 푸터(다음 페이지 트리거)가 미리 돌지 않게
        var hasMore = false
        var error: String? = nil
    }

    enum Action {
        case refresh
        case loadMore
    }
}
