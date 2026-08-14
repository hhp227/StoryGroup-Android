import Combine
import Foundation
import Shared

/// 홈 통합검색 — composeApp SearchViewModel.kt와 1:1 미러.
/// results가 nil이면 검색 전(빈 상태 안내). users 섹션 친구 여부는 init에서 친구 목록을 읽어 friendIds로 판정.
final class SearchViewModel: MviViewModel {
    /// 일회성 이벤트 없음 — 결과 탭 push는 뷰 몫(Kotlin EVENT=Nothing 미러)
    typealias Event = Never

    @Published private(set) var uiState = UiState()

    private let searchUseCase: SearchUseCase

    private let getFriendsUseCase: GetFriendsUseCase

    private let addFriendUseCase: AddFriendUseCase

    private let removeFriendUseCase: RemoveFriendUseCase

    func onAction(_ action: Action) {
        switch action {
        case .search(let query): search(query: query)
        case .clearResults:
            uiState.results = nil
            uiState.error = nil
        case .addFriend(let user): addFriend(user: user)
        case .removeFriend(let userId): removeFriend(userId: userId)
        case .dismissActionError: uiState.actionError = nil
        }
    }

    /// 통합검색(제출 기반) — 빈 검색어는 초기 화면 복귀(친구 탭 미러, 서버 400 선차단)
    private func search(query: String) {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)

        if trimmed.isEmpty {
            uiState.results = nil
            uiState.error = nil
            return
        }
        if uiState.isSearching { return }

        uiState.isSearching = true
        uiState.error = nil
        Task { @MainActor in
            do {
                let results = try await searchUseCase.invoke(query: trimmed)
                uiState.isSearching = false
                uiState.results = results
            } catch {
                uiState.isSearching = false
                uiState.error = error.kotlinMessage(fallback: "검색에 실패했습니다.")
            }
        }
    }

    /// users 섹션 버튼 판정용 — 실패해도 검색은 동작해야 하므로 조용히 무시(버튼은 "친구 추가" 기본)
    private func loadFriendIds() {
        Task { @MainActor in
            if let friends = try? await getFriendsUseCase.invoke() {
                uiState.friendIds = Set(friends.map { $0.userId })
            }
        }
    }

    /// 친구 등록 — friendIds만 낙관적 갱신(이 화면엔 친구 목록이 없다). 409는 서버 문구 노출
    private func addFriend(user: UserSearchResult) {
        if uiState.processingUserId != nil { return }

        uiState.processingUserId = user.id
        uiState.actionError = nil
        Task { @MainActor in
            do {
                try await addFriendUseCase.invoke(userId: user.id)
                uiState.processingUserId = nil
                uiState.friendIds.insert(user.id)
            } catch {
                uiState.processingUserId = nil
                uiState.actionError = error.kotlinMessage(fallback: "친구 등록에 실패했습니다.")
            }
        }
    }

    private func removeFriend(userId: Int64) {
        if uiState.processingUserId != nil { return }

        uiState.processingUserId = userId
        uiState.actionError = nil
        Task { @MainActor in
            do {
                try await removeFriendUseCase.invoke(userId: userId)
                uiState.processingUserId = nil
                uiState.friendIds.remove(userId)
            } catch {
                uiState.processingUserId = nil
                uiState.actionError = error.kotlinMessage(fallback: "친구 해제에 실패했습니다.")
            }
        }
    }

    init(
        searchUseCase: SearchUseCase,
        getFriendsUseCase: GetFriendsUseCase,
        addFriendUseCase: AddFriendUseCase,
        removeFriendUseCase: RemoveFriendUseCase
    ) {
        self.searchUseCase = searchUseCase
        self.getFriendsUseCase = getFriendsUseCase
        self.addFriendUseCase = addFriendUseCase
        self.removeFriendUseCase = removeFriendUseCase
        loadFriendIds()
    }

    struct UiState {
        /// nil=검색 전(빈 상태 안내) — 웹 /search results 상태 축 미러
        var results: SearchResults? = nil
        var isSearching = false
        /// 검색 실패 문구 — 직전 결과를 대체하지 않고 위에 얹는다(친구 탭 미러)
        var error: String? = nil
        /// users 섹션 "친구 추가/해제" 토글 판정 — init 로드+낙관적 갱신
        var friendIds: Set<Int64> = []
        /// 추가/해제 버튼 로딩 표시 — 동시에 하나만 처리(친구 탭 미러)
        var processingUserId: Int64? = nil
        var actionError: String? = nil
    }

    enum Action {
        case search(query: String)
        case clearResults
        case addFriend(user: UserSearchResult)
        case removeFriend(userId: Int64)
        case dismissActionError
    }
}
