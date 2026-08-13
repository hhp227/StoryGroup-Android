import Combine
import Foundation
import Shared

/// 친구 탭 — composeApp FriendsViewModel.kt와 1:1 미러.
/// searchResults가 nil이면 검색 전(친구 목록), 채워지면 검색 결과 — 웹 /search의 results 상태 축 미러.
/// 웹은 한 번 검색하면 친구 목록으로 못 돌아가지만 여기는 검색어를 비우면 즉시 복귀한다.
final class FriendsViewModel: MviViewModel {
    @Published private(set) var uiState = UiState()

    let event = PassthroughSubject<Event, Never>()

    private let getFriendsUseCase: GetFriendsUseCase

    private let addFriendUseCase: AddFriendUseCase

    private let removeFriendUseCase: RemoveFriendUseCase

    private let searchUsersUseCase: SearchUsersUseCase

    private let openDirectRoomUseCase: OpenDirectRoomUseCase

    func onAction(_ action: Action) {
        switch action {
        case .refresh: refresh()
        case .search(let query): search(query: query)
        case .addFriend(let user): addFriend(user: user)
        case .removeFriend(let userId): removeFriend(userId: userId)
        case .openDm(let userId, let userName): openDm(userId: userId, userName: userName)
        case .dismissActionError: uiState.actionError = nil
        }
    }

    /// 친구 목록 로드 — 웹처럼 마운트(VM 생성=세션 진입) 1회 + 에러 재시도(웹 listFriends 미러)
    private func refresh() {
        if uiState.isLoading { return }

        uiState.isLoading = true
        uiState.error = nil
        Task { @MainActor in
            do {
                let friends = try await getFriendsUseCase.invoke()
                uiState.isLoading = false
                uiState.friends = friends
            } catch {
                uiState.isLoading = false
                uiState.error = error.kotlinMessage(fallback: "친구 목록을 불러오지 못했습니다.")
            }
        }
    }

    /// 사용자 검색(제출 기반) — 빈 검색어는 친구 목록 복귀(웹과 달리 복귀 가능)
    private func search(query: String) {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)

        if trimmed.isEmpty {
            uiState.searchResults = nil
            uiState.searchError = nil
            return
        }
        if uiState.isSearching { return }

        uiState.isSearching = true
        uiState.searchError = nil
        Task { @MainActor in
            do {
                let users = try await searchUsersUseCase.invoke(query: trimmed)
                uiState.isSearching = false
                uiState.searchResults = users
            } catch {
                uiState.isSearching = false
                uiState.searchError = error.kotlinMessage(fallback: "검색에 실패했습니다.")
            }
        }
    }

    /// 친구 등록 — 재조회 없이 검색 결과를 친구로 변환해 이름순 위치에 끼워 넣는다(웹 낙관적 갱신 미러)
    private func addFriend(user: UserSearchResult) {
        if uiState.processingUserId != nil { return }

        uiState.processingUserId = user.id
        uiState.actionError = nil
        Task { @MainActor in
            do {
                try await addFriendUseCase.invoke(userId: user.id)
                let added = Friend(
                    userId: user.id,
                    name: user.name,
                    profileImg: user.profileImg,
                    statusMessage: user.statusMessage,
                    friendedAt: ""
                )
                uiState.processingUserId = nil
                uiState.friends = (uiState.friends + [added]).sorted { $0.name < $1.name }
            } catch {
                uiState.processingUserId = nil
                uiState.actionError = error.kotlinMessage(fallback: "친구 등록에 실패했습니다.")
            }
        }
    }

    /// 친구 해제 — 성공 시 목록에서만 제거(웹 removeFriend 미러). 검색 결과 토글도 friendIds로 따라온다
    private func removeFriend(userId: Int64) {
        if uiState.processingUserId != nil { return }

        uiState.processingUserId = userId
        uiState.actionError = nil
        Task { @MainActor in
            do {
                try await removeFriendUseCase.invoke(userId: userId)
                uiState.processingUserId = nil
                uiState.friends.removeAll { $0.userId == userId }
            } catch {
                uiState.processingUserId = nil
                uiState.actionError = error.kotlinMessage(fallback: "친구 해제에 실패했습니다.")
            }
        }
    }

    /// 친구와 1:1 DM 열기 — get-or-create(멱등)라 이미 방이 있으면 그 방으로 간다(웹 "메시지" 버튼 미러)
    private func openDm(userId: Int64, userName: String) {
        if uiState.isOpeningDm { return }

        uiState.isOpeningDm = true
        uiState.actionError = nil
        Task { @MainActor in
            do {
                let chatRoomId = try await openDirectRoomUseCase.invoke(otherUserId: userId)
                uiState.isOpeningDm = false
                // 방 이름은 서버가 "DM" 고정이라 상대 이름을 제목으로 넘긴다(허브와 동일)
                event.send(.dmOpened(chatRoomId: chatRoomId.int64Value, title: userName))
            } catch {
                // 차단 관계(403 BLOCKED) 등 — 목록 상단에 표시된다
                uiState.isOpeningDm = false
                uiState.actionError = error.kotlinMessage(fallback: "DM을 열지 못했습니다.")
            }
        }
    }

    init(
        getFriendsUseCase: GetFriendsUseCase,
        addFriendUseCase: AddFriendUseCase,
        removeFriendUseCase: RemoveFriendUseCase,
        searchUsersUseCase: SearchUsersUseCase,
        openDirectRoomUseCase: OpenDirectRoomUseCase
    ) {
        self.getFriendsUseCase = getFriendsUseCase
        self.addFriendUseCase = addFriendUseCase
        self.removeFriendUseCase = removeFriendUseCase
        self.searchUsersUseCase = searchUsersUseCase
        self.openDirectRoomUseCase = openDirectRoomUseCase
        refresh()
    }

    struct UiState {
        /// 서버가 이름순으로 정렬해 준다 — 낙관적 갱신도 이름순 위치를 유지한다
        var friends: [Friend] = []
        var isLoading = false
        var error: String? = nil
        /// nil=검색 전(친구 목록 표시) — 웹 /search의 results 상태 축 미러
        var searchResults: [UserSearchResult]? = nil
        var isSearching = false
        /// 검색 실패 문구 — 직전 표시(친구 목록/이전 결과)를 대체하지 않고 위에 얹는다
        var searchError: String? = nil
        /// 추가/해제 버튼 로딩 표시용 — 동시에 하나만 처리(웹 busy 미러)
        var processingUserId: Int64? = nil
        var isOpeningDm = false
        /// 추가/해제/DM 실패 문구 — 로드 에러(error)와 달리 목록 화면을 대체하지 않는다
        var actionError: String? = nil

        /// 검색 결과의 "친구 추가/해제" 토글 판정 — 응답에 친구 여부가 없어 친구 목록과 대조한다(웹 friendIds 미러)
        var friendIds: Set<Int64> { Set(friends.map { $0.userId }) }
    }

    enum Action {
        case refresh
        case search(query: String)
        case addFriend(user: UserSearchResult)
        case removeFriend(userId: Int64)
        case openDm(userId: Int64, userName: String)
        case dismissActionError
    }

    enum Event {
        /// DM 방 확보 성공 — 화면이 채팅방(groupId=nil)으로 push한다
        case dmOpened(chatRoomId: Int64, title: String)
    }
}
