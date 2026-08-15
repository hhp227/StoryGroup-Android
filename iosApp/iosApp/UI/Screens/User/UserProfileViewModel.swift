import Combine
import Foundation
import Shared

/// 공개 프로필 — composeApp UserProfileViewModel.kt와 1:1 미러.
/// 친구 여부는 응답에 없어 친구 목록과 대조하며, 목록 로드 실패 시 isFriend=nil 유지 →
/// 화면이 친구 버튼을 숨긴다(웹 isFriend===null 미러).
final class UserProfileViewModel: MviViewModel {
    @Published private(set) var uiState: UiState

    let event = PassthroughSubject<Event, Never>()

    private let userId: Int64

    private let getPublicProfileUseCase: GetPublicProfileUseCase

    private let getFriendsUseCase: GetFriendsUseCase

    private let addFriendUseCase: AddFriendUseCase

    private let removeFriendUseCase: RemoveFriendUseCase

    private let openDirectRoomUseCase: OpenDirectRoomUseCase

    func onAction(_ action: Action) {
        switch action {
        case .refresh: load()
        case .toggleFriend: toggleFriend()
        case .openDm: openDm()
        case .dismissActionError: uiState.actionError = nil
        }
    }

    private func load() {
        if uiState.isLoading { return }

        uiState.isLoading = true
        uiState.loadError = nil
        Task { @MainActor in
            do {
                let profile = try await getPublicProfileUseCase.invoke(userId: userId)
                uiState.isLoading = false
                uiState.profile = profile
            } catch {
                uiState.isLoading = false
                uiState.loadError = error.kotlinMessage(fallback: "프로필을 불러오지 못했습니다.")
            }
        }
    }

    /// 친구 버튼 상태용 — 실패는 조용히 무시하고 isFriend=nil 유지(버튼 숨김, 웹 미러)
    private func loadFriendState() {
        if uiState.isSelf { return }

        Task { @MainActor in
            if let friends = try? await getFriendsUseCase.invoke() {
                uiState.isFriend = friends.contains { $0.userId == userId }
            }
        }
    }

    /// 친구 토글 — 성공 시 isFriend만 뒤집는다(웹 미러). 409 등은 서버 문구
    private func toggleFriend() {
        guard let isFriend = uiState.isFriend else { return }

        if uiState.isBusy { return }

        uiState.isBusy = true
        uiState.actionError = nil
        Task { @MainActor in
            do {
                if isFriend {
                    try await removeFriendUseCase.invoke(userId: userId)
                } else {
                    try await addFriendUseCase.invoke(userId: userId)
                }
                uiState.isBusy = false
                uiState.isFriend = !isFriend
            } catch {
                uiState.isBusy = false
                uiState.actionError = error.kotlinMessage(fallback: "친구 처리에 실패했습니다.")
            }
        }
    }

    /// 1:1 DM — get-or-create(멱등). 방 이름은 서버가 "DM" 고정이라 상대 이름을 제목으로(친구 탭 미러)
    private func openDm() {
        guard let profile = uiState.profile else { return }

        if uiState.isOpeningDm { return }

        uiState.isOpeningDm = true
        uiState.actionError = nil
        Task { @MainActor in
            do {
                let chatRoomId = try await openDirectRoomUseCase.invoke(otherUserId: userId)
                uiState.isOpeningDm = false
                event.send(.dmOpened(chatRoomId: chatRoomId.int64Value, title: profile.name))
            } catch {
                // 차단 관계(403 BLOCKED) 등 — 인라인 문구로 노출
                uiState.isOpeningDm = false
                uiState.actionError = error.kotlinMessage(fallback: "DM을 시작하지 못했습니다.")
            }
        }
    }

    init(
        userId: Int64,
        getPublicProfileUseCase: GetPublicProfileUseCase,
        getFriendsUseCase: GetFriendsUseCase,
        addFriendUseCase: AddFriendUseCase,
        removeFriendUseCase: RemoveFriendUseCase,
        openDirectRoomUseCase: OpenDirectRoomUseCase,
        getCurrentUserIdUseCase: GetCurrentUserIdUseCase
    ) {
        self.userId = userId
        self.getPublicProfileUseCase = getPublicProfileUseCase
        self.getFriendsUseCase = getFriendsUseCase
        self.addFriendUseCase = addFriendUseCase
        self.removeFriendUseCase = removeFriendUseCase
        self.openDirectRoomUseCase = openDirectRoomUseCase
        uiState = UiState(isSelf: getCurrentUserIdUseCase.invoke()?.int64Value == userId)
        load()
        loadFriendState()
    }

    struct UiState {
        var profile: PublicProfile? = nil
        var isLoading = false
        var loadError: String? = nil
        /// nil=친구 여부 판정 불가(목록 로드 실패) → 화면이 친구 버튼을 숨긴다(웹 미러)
        var isFriend: Bool? = nil
        /// 친구 토글 진행 중 — 버튼 비활성(웹 isBusy 미러)
        var isBusy = false
        var isOpeningDm = false
        var actionError: String? = nil
        /// 본인 여부(JWT sub 대조) — 본인이면 액션이 "프로필 수정"뿐(웹 isSelf 미러)
        var isSelf = false

        init(isSelf: Bool = false) {
            self.isSelf = isSelf
        }
    }

    enum Action {
        case refresh
        case toggleFriend
        case openDm
        case dismissActionError
    }

    enum Event {
        /// DM 방 확보 성공 — 화면이 채팅방(groupId=nil)으로 push한다
        case dmOpened(chatRoomId: Int64, title: String)
    }
}
