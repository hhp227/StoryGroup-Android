import Foundation
import Combine

/// 네비게이션 — composeApp ui/navigation/NavigationViewModel.kt와 1:1 미러.
/// 탭과 화면 간 결과는 상태로 소유하고, 오버레이 이동만 이벤트로 호스트(NavigationStackCompat)에 위임한다.
/// 백스택 자체는 NavigationStackCompat(Route path 배열)가 소유한다 — 시스템 뒤로가기와
/// 프로세스 복원을 다시 만들지 않는다.
/// 세션 스코프에 선언한다 — 로그아웃 시 pendingResults까지 함께 정리된다.
/// ⚠️ currentTab은 프로세스 사망을 못 버틴다(SavedStateHandle을 안 받는 평범한 상태라
/// Kotlin과 동일한 트레이드오프 — iOS 쪽 동급은 @SceneStorage, 옛 셸의 @State는 그것도 못 버텼다).
final class NavigationViewModel: ObservableObject, MviViewModel {

    struct UiState {
        var currentTab: SGDestination = .home
        /// 화면이 재진입해 읽어갈 때까지 남는다
        var pendingResults: Set<NavResult> = []
    }

    @Published private(set) var uiState = UiState()

    /// Kotlin은 한 틱에 이벤트가 몰릴 수 있어 버퍼 8칸+DROP_OLDEST가 필요하지만,
    /// PassthroughSubject는 send()가 동기 전달이라 버퍼 설정 자체가 없다.
    let event = PassthroughSubject<NavigationEvent, Never>()

    func onAction(_ action: NavigationAction) {
        switch action {
        // 탭은 상태다 — 셸이 uiState.currentTab을 그린다
        case .selectTab(let destination):
            uiState.currentTab = destination

        case .publishResult(let result):
            uiState.pendingResults.insert(result)

        case .consumeResult(let result):
            uiState.pendingResults.remove(result)

        case .navigateBack:
            event.send(.navigateBack)

        case .openChatRoomFromProfile(let chatRoomId, let groupId, let title, let underlyingChatRoomId):
            if underlyingChatRoomId == chatRoomId {
                event.send(.navigateBack)
            } else {
                event.send(.navigateTo(.chatRoom(chatRoomId: chatRoomId, groupId: groupId, title: title)))
            }

        case .navigateToGroupDetail(let groupId):
            event.send(.navigateTo(.groupDetail(groupId: groupId)))

        case .navigateToPostDetail(let groupId, let postId):
            event.send(.navigateTo(.postDetail(groupId: groupId, postId: postId)))

        case .navigateToChatRoom(let chatRoomId, let groupId, let title):
            event.send(.navigateTo(.chatRoom(chatRoomId: chatRoomId, groupId: groupId, title: title)))

        case .navigateToUserProfile(let userId):
            event.send(.navigateTo(.userProfile(userId: userId)))

        case .navigateToCreatePost(let groupId, let postId):
            event.send(.navigateTo(.createPost(groupId: groupId, postId: postId)))

        case .navigateToGroupEdit(let groupId):
            event.send(.navigateTo(.groupEdit(groupId: groupId)))

        case .navigateToGroupReports(let groupId):
            event.send(.navigateTo(.groupReports(groupId: groupId)))

        // 발신 — 입장+벨울림
        case .startCall(let chatRoomId, let title, let video):
            event.send(.navigateTo(.call(chatRoomId: chatRoomId, title: title, ring: true, video: video)))

        // 수신 배너 수락 — 입장만(벨은 다시 울리지 않는다)
        case .acceptIncomingCall(let chatRoomId, let title, let video):
            event.send(.navigateTo(.call(chatRoomId: chatRoomId, title: title, ring: false, video: video)))

        case .navigateToAccountSettings:
            event.send(.navigateTo(.accountSettings))

        case .navigateToAppSettings:
            event.send(.navigateTo(.appSettings))

        case .navigateToBlockedUsers:
            event.send(.navigateTo(.blockedUsers))

        case .navigateToCreateGroup:
            event.send(.navigateTo(.createGroup))

        case .navigateToDiscoverGroups:
            event.send(.navigateTo(.discoverGroups))

        case .navigateToSearch:
            event.send(.navigateTo(.search))
        }
    }
}
