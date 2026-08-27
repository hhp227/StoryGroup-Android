import SwiftUI
import Shared
// SwiftUI.Group(뷰)과 도메인 모델 Group의 동명 충돌 — 이 파일의 Group은 도메인 모델로 고정
import class Shared.Group

// 내비게이션 쉘 — Compose ui/shell/MainShell.kt 미러.
// 설정>내비게이션 스타일에 따라 하단 탭/레거시 드로어를 교체하고, 화면은 두 쉘이 공유한다.

enum SGDestination: String, CaseIterable, Identifiable {
    case home, groups, friends, chat, notifications, profile

    var id: String { rawValue }

    var label: String {
        switch self {
        case .home: return "홈"
        case .groups: return "그룹"
        case .friends: return "친구"
        case .chat: return "채팅"
        case .notifications: return "알림"
        case .profile: return "프로필"
        }
    }

    var systemImage: String {
        switch self {
        case .home: return "house.fill"
        case .groups: return "person.3.fill"
        case .friends: return "person.2.fill"
        case .chat: return "bubble.left.fill"
        case .notifications: return "bell.fill"
        case .profile: return "person.crop.circle"
        }
    }

    /// 탭 5개(홈·그룹·친구·채팅·프로필) — 알림은 헤더 우측 종 아이콘으로 진입(드로어는 전부 노출)
    var inTabs: Bool { self != .notifications }
}

/// 공개 프로필 시트에서 고른 후속 push 대상 — 시트가 완전히 닫힌 뒤(onDismiss) 실행해야 유실되지 않는다
private enum ProfileFollowUp {
    case chatRoom(ChatRoomRef)
    case accountSettings
}

struct MainShellView: View {
    @ObservedObject var theme: SGThemeState

    let onLogout: () -> Void

    /// 프로필 화면과 드로어 헤더가 공유하는 세션 상태 — 공유 소유자(셸)가 선언한다.
    /// 화면 전용 VM(홈/그룹)은 각 화면(HomeView/GroupsView)이 소유한다(Compose default parameter 미러).
    @StateObject private var profileViewModel = ProfileViewModel()

    /// 종 아이콘 뱃지와 알림 화면이 공유 — Compose sessionNotificationsViewModel 미러
    @StateObject private var notificationsViewModel = NotificationsViewModel()

    /// 채팅 탭 뱃지·허브·채팅방 진입/이탈 신호가 공유 — Compose sessionChatViewModel 미러
    @StateObject private var chatViewModel = ChatViewModel()

    /// 수신 통화 배너(DM·그룹 방) — 개인 큐 CALL_INVITE를 세션 전역에서 받는다(Compose 미러)
    @StateObject private var incomingCallViewModel = IncomingCallViewModel()

    /// 채팅방 우측 드로어 게시대 — 방이 세션을 열면 아래 오버레이가 내비 컨테이너 밖에서 그린다.
    /// Compose ChatRoomDrawer가 스크림으로 상단바까지 덮는 것의 iOS 등가(수신 콜 배너와 같은 층)
    @StateObject private var chatRoomDrawerHost = ChatRoomDrawerHost()

    /// 내비게이션 상태 머신 — composeApp ui/navigation/NavigationViewModel.kt 1:1 미러(세션 스코프,
    /// 로그아웃 시 pendingResults까지 함께 정리된다). 탭(currentTab)과 화면 간 결과(pendingResults)는
    /// 상태로 소유하고, 오버레이 이동만 event로 호스트(NavigationStackCompat)에 위임한다
    @StateObject private var navigationViewModel = NavigationViewModel()

    /// 오버레이 백스택 — Compose NavHostController(Route path) 미러. 비어 있으면 셸 자체이고
    /// (Route.Shell 없음), 쌓이면 그 위 화면이 셸(탭바 포함) 전체를 덮는다
    @State private var path: [Route] = []

    /// 공개 프로필 시트 — Compose dialog<UserProfileRoute> 미러(친구 탭 행 발 진입).
    /// path에 쌓이는 대상이 아니다 — onReceive가 .userProfile 이벤트를 걸러 여기로 돌린다
    @State private var selectedUserId: Int64? = nil

    /// 프로필 시트의 후속 이동(채팅방/계정 설정) — 시트 dismiss 완료 후 push한다
    @State private var profileFollowUp: ProfileFollowUp? = nil

    var body: some View {
        navigationRoot
            // 채팅방(어느 진입 경로든)이 드로어 세션을 열 수 있게 게시대를 내비 트리 전체에 주입
            .environmentObject(chatRoomDrawerHost)
            .onReceive(navigationViewModel.event) { event in
                switch event {
                case .navigateTo(let route):
                    // 공개 프로필만 시트다(Compose dialog<UserProfileRoute> 미러) — path에 쌓지 않는다
                    if case .userProfile(let userId) = route {
                        selectedUserId = userId
                    } else {
                        path.append(route)
                    }
                case .navigateBack:
                    if selectedUserId != nil {
                        selectedUserId = nil
                    } else if !path.isEmpty {
                        path.removeLast()
                    }
                }
            }
            // 공개 프로필 시트 — Compose dialog<UserProfileRoute> 미러. 후속 이동(채팅방/계정 설정)은
            // 시트가 완전히 닫힌 뒤(onDismiss)에 push해야 유실되지 않는다
            .sheet(isPresented: showUserProfile, onDismiss: runProfileFollowUp) { userProfileDestination }
            // 채팅방 우측 드로어 — 내비 컨테이너 밖이라 스크림이 내비바까지 덮는다(Compose 미러).
            // 수신 콜 배너보다 아래 레이어에 둔다(배너는 드로어 위에도 떠야 한다)
            .overlay { ChatRoomDrawerOverlay(host: chatRoomDrawerHost) }
            // 수신 통화 배너 — 어떤 화면 위에서든 뜬다(Compose Box 최상단 오버레이 미러)
            .overlay(alignment: .top) {
                if let call = incomingCallViewModel.uiState.incomingCall {
                    SGIncomingCallBanner(
                        call: call,
                        onAccept: {
                            incomingCallViewModel.onAction(.dismiss)
                            // 수락 = 통화 화면 진입(구독=입장) — 벨울림은 다시 보내지 않는다
                            // (NavigationViewModel이 acceptIncomingCall을 ring=false로 채워 보낸다).
                            // 제목은 그룹 방이면 방(그룹) 이름, DM이면 발신자 이름(Compose와 동일 규칙).
                            // 보이스톡이면 수신 측도 카메라 OFF로 입장한다(발신 모드 미러)
                            navigationViewModel.onAction(.acceptIncomingCall(
                                chatRoomId: call.chatRoomId,
                                title: call.roomName ?? call.callerName,
                                video: call.video
                            ))
                        },
                        onDecline: { incomingCallViewModel.onAction(.dismiss) }
                    )
                    .padding(.horizontal, 16)
                    .padding(.top, 8)
                }
            }
    }

    /// 루트 내비게이션 컨테이너 — 셸(탭바 포함) 전체가 루트 콘텐츠라 상세 push 시 하단 탭까지 덮는다.
    /// iOS 16/15 분기(NavigationStack vs NavigationView+NavigationLink)는 NavigationStackCompat이
    /// 대신 처리한다(Task 9 계약) — 이 파일은 path 배열과 목적지 매핑만 신경 쓰면 된다
    @ViewBuilder private var navigationRoot: some View {
        NavigationStackCompat(path: $path) {
            shellContent
        } destination: { route in
            destinationView(for: route)
        }
    }

    @ViewBuilder private var shellContent: some View {
        if theme.navStyle == .tabs {
            TabShellView(
                current: navigationViewModel.uiState.currentTab,
                onNavigationAction: navigationViewModel.onAction,
                pendingResults: navigationViewModel.uiState.pendingResults,
                profile: profileViewModel.uiState.profile,
                notificationsViewModel: notificationsViewModel,
                chatViewModel: chatViewModel,
                profileViewModel: profileViewModel,
                onLogout: onLogout
            )
        } else {
            DrawerShellView(
                current: navigationViewModel.uiState.currentTab,
                onNavigationAction: navigationViewModel.onAction,
                pendingResults: navigationViewModel.uiState.pendingResults,
                profile: profileViewModel.uiState.profile,
                notificationsViewModel: notificationsViewModel,
                chatViewModel: chatViewModel,
                profileViewModel: profileViewModel,
                onLogout: onLogout
            )
        }
    }

    /// path에 쌓인 Route → 실제 화면 매핑. 각 case의 생성자 인자는 그 View의 실제 init을 그대로
    /// 따른다(이 스위치가 만든 게 아니라 기존 xxxDestination 프로퍼티/화면 내부 push에서 그대로 옮김).
    /// 화면 내부 NavigationLink(그룹 상세 15곳 등)는 이번 범위 밖이라 손대지 않는다 — 그래서 postDetail/
    /// groupEdit/groupReports/createPost/createGroup/discoverGroups는 오늘은 화면 내부에서만 열리고, 이
    /// case들은 후속 작업(화면 내부 링크 중앙화)이 실제로 onNavigationAction을 보내기 전까지는 도달 불가다.
    @ViewBuilder private func destinationView(for route: Route) -> some View {
        switch route {
        case .groupDetail(let groupId):
            GroupDetailView(
                groupId: groupId,
                chatViewModel: chatViewModel,
                theme: theme,
                profileViewModel: profileViewModel,
                onGroupClosed: {
                    // 나간/삭제한 그룹이 목록에 남지 않게 — 그룹 탭이 결과를 소비해 refresh한다
                    // (Compose GroupDetailScreen onGroupClosed → PublishResult(GroupsChanged)+NavigateBack 미러)
                    navigationViewModel.onAction(.publishResult(.groupsChanged))
                    navigationViewModel.onAction(.navigateBack)
                },
                onGroupUpdated: {
                    // 그룹 정보 수정 저장 — 목록 카드의 이름·커버 갱신 신호(pop 없음)
                    navigationViewModel.onAction(.publishResult(.groupsChanged))
                }
            )
        case .postDetail(let groupId, let postId):
            PostDetailView(
                groupId: groupId,
                postId: postId,
                chatViewModel: chatViewModel,
                profileViewModel: profileViewModel
            )
        case .chatRoom(let chatRoomId, let groupId, let title):
            ChatRoomView(
                chatRoomId: chatRoomId,
                groupId: groupId,
                title: title,
                chatViewModel: chatViewModel
            )
        case .userProfile:
            // 시트로 처리한다(Compose dialog<UserProfileRoute> 미러) — onReceive가 걸러서 여기로는 안 온다
            EmptyView()
        case .createPost(let groupId, let postId):
            CreatePostView(groupId: groupId, postId: postId) {
                // 성공 시 결과를 publish — 수정이면 상세가, 신규면 피드가 읽어간다(Compose CreatePostScreen
                // Event.Created 미러). CreatePostView가 스스로 dismiss()해서 닫으므로 여기서
                // navigateBack은 부르지 않는다(부르면 path가 두 칸 줄어든다).
                // ⚠️ path 경유 postDetail(알림 행 탭)이 생겼지만 PostDetailView는 아직 pendingResults를
                // 읽지 않는다 — 이 결과는 소비자가 생기기 전까지 Set에 남아만 있는다(화면 내부 경로도 동일)
                if let groupId, let postId {
                    navigationViewModel.onAction(.publishResult(.postUpdated(groupId: groupId, postId: postId)))
                } else {
                    navigationViewModel.onAction(.publishResult(.postCreated(groupId: groupId)))
                }
            }
        case .groupEdit(let groupId):
            GroupEditView(groupId: groupId) {
                // 저장 성공 — 상세·목록 갱신 신호 발행 후 복귀(Compose GroupEditScreen onSaved 미러).
                // GroupEditView는(계정 설정과 달리) 스스로 pop하지 않으므로 여기서 navigateBack까지 부른다.
                // ⚠️ groupUpdated도 postCreated/postUpdated와 같은 사정 — 오늘 iOS에서 pendingResults를
                // 읽는 곳은 그룹 탭의 groupsChanged 체크뿐이라(TabShellView/DrawerShellView) 소비자가
                // 없다. 후속 작업은 소비자도 같이 추가해야 한다(발행만 하고 끝나면 안 된다)
                navigationViewModel.onAction(.publishResult(.groupUpdated(groupId: groupId)))
                navigationViewModel.onAction(.publishResult(.groupsChanged))
                navigationViewModel.onAction(.navigateBack)
            }
        case .groupReports(let groupId):
            GroupReportsView(
                groupId: groupId,
                chatViewModel: chatViewModel,
                profileViewModel: profileViewModel
            )
        case .call(let chatRoomId, let title, let ring, let video):
            CallView(chatRoomId: chatRoomId, title: title, ring: ring, video: video)
        case .accountSettings:
            // 세션 ProfileViewModel을 넘겨 저장 성공 시 프로필 탭/드로어 헤더가 갱신되게 한다
            AccountSettingsView(profileViewModel: profileViewModel)
        case .appSettings:
            SGSettingsView(theme: theme)
        case .blockedUsers:
            // 차단 사용자 관리 — 프로필 탭 메뉴 진입(웹 /settings/blocked 미러)
            BlockedUsersView()
        case .createGroup, .discoverGroups:
            // ⚠️ CreateGroupView/DiscoverGroupsView는 GroupsViewModel(그룹 탭과 같은 인스턴스)을 요구한다.
            // Kotlin은 sessionViewModel로 어디서든 같은 인스턴스를 돌려받지만 iOS엔 그런 세션 저장소가
            // 없어 셸은 그 인스턴스를 갖고 있지 않다. GroupsView.swift가 화면 내부에서 자신의 viewModel을
            // 직접 넘겨 여는 것이 지금 유일하게 올바른 경로라, 이 두 case는 화면 내부 링크 중앙화(후속
            // 작업)에서 GroupsViewModel도 같이 정리되기 전까지 자리만 채운다.
            // EmptyView는 렌더 노드가 없어 onAppear가 발화하지 않을 수 있다 — 실제 body가 있는 뷰로
            // 채워 디버그 빌드는 크래시로, 릴리즈는 최소한 설명 가능한 화면으로 보이게 한다
            Text("준비 중인 화면입니다")
                .onAppear {
                    assertionFailure("Route.\(route)는 아직 셸에서 열 수 없다 — GroupsViewModel 세션 스코프화 선행 필요")
                }
        case .search:
            SearchView(
                chatViewModel: chatViewModel,
                theme: theme,
                profileViewModel: profileViewModel,
                onGroupsRefreshNeeded: { navigationViewModel.onAction(.publishResult(.groupsChanged)) }
            )
        }
    }

    @ViewBuilder private var userProfileDestination: some View {
        if let userId = selectedUserId {
            UserProfileView(
                userId: userId,
                onOpenChatRoom: { room in
                    profileFollowUp = .chatRoom(room)
                    selectedUserId = nil
                },
                onOpenAccountSettings: {
                    profileFollowUp = .accountSettings
                    selectedUserId = nil
                }
            )
        }
    }

    /// 프로필 시트 dismiss 완료 후 후속 push 실행 — 드래그로 닫으면 followUp이 nil이라 아무 일 없다.
    /// 시트가 완전히 닫히기 전에 push하면 SwiftUI가 push를 흘려버려서 반드시 onDismiss에서 해야 한다
    private func runProfileFollowUp() {
        guard let followUp = profileFollowUp else { return }
        profileFollowUp = nil

        switch followUp {
        case .chatRoom(let room):
            // same-room guard는 2단계에서 — 오늘 iOS는 항상 push한다(Compose는 OpenChatRoomFromProfile로
            // 같은 방이면 닫기만)
            path.append(.chatRoom(chatRoomId: room.chatRoomId, groupId: room.groupId, title: room.title))
        case .accountSettings:
            path.append(.accountSettings)
        }
    }

    /// 시트를 닫으면(X·드래그) selectedUserId를 nil로 되돌리는 브리지
    private var showUserProfile: Binding<Bool> {
        Binding(
            get: { selectedUserId != nil },
            set: { if !$0 { selectedUserId = nil } }
        )
    }

    init(theme: SGThemeState, onLogout: @escaping () -> Void) {
        self.theme = theme
        self.onLogout = onLogout
    }
}

/// 두 쉘이 공유하는 목적지 → 화면 매핑 — Compose DestinationContent 미러.
/// 상단바는 전 탭 기본 NavigationBar로 통일(사용자 지시) — 홈은 콜랩싱 헤더 때문에
/// 내비바(제목·툴바)는 셸이 루트 NavigationStack 위에서 목적지별로 구성한다.
struct DestinationView: View {
    let destination: SGDestination

    let profile: Profile?

    /// 홈 체인(게시글 상세→작성자 프로필→계정 설정)이 쓴다 — 셸 소유 세션 VM
    let profileViewModel: ProfileViewModel

    /// 셸 소유 세션 VM — 알림 화면이 종 뱃지와 같은 인스턴스를 쓴다(ProfileViewModel 주입 선례)
    let notificationsViewModel: NotificationsViewModel

    /// 셸 소유 세션 VM — 허브가 채팅 탭 뱃지와 같은 인스턴스를 쓴다
    let chatViewModel: ChatViewModel

    /// 그룹 상세 push — Compose onOpenGroupDetail 미러. TabShellView/DrawerShellView가
    /// onNavigationAction(.navigateToGroupDetail)로 어댑팅해 넘긴다(MainShellView path)
    let onOpenGroup: (Group) -> Void

    /// 채팅방 push — Compose onOpenChatRoom 미러. TabShellView/DrawerShellView가
    /// onNavigationAction(.navigateToChatRoom)으로 어댑팅해 넘긴다(MainShellView path)
    let onOpenChatRoom: (ChatRoomRef) -> Void

    /// 친구 탭 행 탭 → 공개 프로필(웹 /users/[id] 미러) — MainShellView로 위임
    let onOpenUserProfile: (Int64) -> Void

    /// 알림 행 탭 → 게시글 상세 push — Compose onOpenPost 미러. TabShellView/DrawerShellView가
    /// onNavigationAction(.navigateToPostDetail)로 어댑팅해 넘긴다(MainShellView path)
    let onOpenPost: (Int64, Int64) -> Void

    /// 상세에서 나가기/삭제 후 복귀 — 그룹 탭이 소비해 목록을 첫 페이지부터 다시 읽는다(Compose groupsRefreshRequested 미러)
    let groupsRefreshRequested: Bool

    let onGroupsRefreshHandled: () -> Void

    let onOpenSettings: () -> Void

    /// 계정 설정 push — Compose onOpenAccountSettings 미러. TabShellView/DrawerShellView가
    /// onNavigationAction(.navigateToAccountSettings)으로 어댑팅해 넘긴다(MainShellView path)
    let onOpenAccountSettings: () -> Void

    /// 차단 사용자 관리 push — Compose onOpenBlockedUsers 미러. TabShellView/DrawerShellView가
    /// onNavigationAction(.navigateToBlockedUsers)로 어댑팅해 넘긴다(MainShellView path)
    let onOpenBlockedUsers: () -> Void

    let onLogout: () -> Void

    var body: some View {
        switch destination {
        case .home:
            HomeView(chatViewModel: chatViewModel, profileViewModel: profileViewModel)
        case .groups:
            GroupsView(
                onOpenGroup: onOpenGroup,
                refreshRequested: groupsRefreshRequested,
                onRefreshHandled: onGroupsRefreshHandled
            )
        case .friends:
            // 친구 탭 "메시지" 버튼 → DM 채팅방(groupId=nil), 행 탭 → 공개 프로필(웹 /users/[id] 미러)
            FriendsView(onOpenChatRoom: onOpenChatRoom, onOpenUserProfile: onOpenUserProfile)
        case .chat:
            ChatView(viewModel: chatViewModel, onOpenChatRoom: onOpenChatRoom)
        case .notifications:
            NotificationsView(viewModel: notificationsViewModel, onOpenPost: onOpenPost)
        case .profile:
            ProfileView(
                profile: profile,
                onOpenAccountSettings: onOpenAccountSettings,
                onOpenSettings: onOpenSettings,
                onOpenBlockedUsers: onOpenBlockedUsers,
                onLogout: onLogout
            )
        }
    }
}
