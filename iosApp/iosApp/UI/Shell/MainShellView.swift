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

struct MainShellView: View {
    @ObservedObject var theme: SGThemeState

    let onLogout: () -> Void

    /// 화면들이 자기 ViewModel을 만들 때 쓴다 — Compose LocalAppContainer 미러
    private let container: AppContainer

    /// 프로필 화면과 드로어 헤더가 공유하는 세션 상태 — 공유 소유자(셸)가 선언한다.
    /// 화면 전용 VM(홈/그룹)은 각 화면(HomeView/GroupsView)이 소유한다(Compose default parameter 미러).
    @StateObject private var profileViewModel: ProfileViewModel

    /// 종 아이콘 뱃지와 알림 화면이 공유 — Compose sessionNotificationsViewModel 미러
    @StateObject private var notificationsViewModel: NotificationsViewModel

    /// 채팅 탭 뱃지·허브·채팅방 진입/이탈 신호가 공유 — Compose sessionChatViewModel 미러
    @StateObject private var chatViewModel: ChatViewModel

    /// 수신 통화 배너(DM·그룹 방) — 개인 큐 CALL_INVITE를 세션 전역에서 받는다(Compose 미러)
    @StateObject private var incomingCallViewModel: IncomingCallViewModel

    @State private var current: SGDestination = .home

    /// 앱 설정 풀스크린 push — Compose 셸 위 풀스크린 오버레이(AppSettingsScreen) 미러
    @State private var showSettings = false

    /// 풀스크린 push 대상 — Compose NavHost(GroupDetailRoute(groupId)) 미러. nil이 아니면 상세가 셸을 통째로 덮는다
    @State private var selectedGroupId: Int64? = nil

    /// 계정 설정 풀스크린 push — Compose NavHost(AccountSettingsRoute) 미러
    @State private var showAccountSettings = false

    /// 채팅방 풀스크린 push — Compose NavHost(ChatRoomRoute) 미러
    @State private var selectedChatRoom: ChatRoomRef? = nil

    /// 수신 통화 수락으로 push할 통화 화면 — Compose CallRoute(ring=false) 미러
    @State private var acceptedCall: CallRef? = nil

    var body: some View {
        navigationRoot
            // 수신 통화 배너 — 어떤 화면 위에서든 뜬다(Compose Box 최상단 오버레이 미러)
            .overlay(alignment: .top) {
                if let call = incomingCallViewModel.uiState.incomingCall {
                    SGIncomingCallBanner(
                        call: call,
                        onAccept: {
                            incomingCallViewModel.onAction(.dismiss)
                            // 수락 = 통화 화면 진입(구독=입장) — 벨울림은 다시 보내지 않는다(ring=false).
                            // 제목은 그룹 방이면 방(그룹) 이름, DM이면 발신자 이름(Compose와 동일 규칙).
                            // 보이스톡이면 수신 측도 카메라 OFF로 입장한다(발신 모드 미러)
                            acceptedCall = CallRef(
                                chatRoomId: call.chatRoomId,
                                title: call.roomName ?? call.callerName,
                                ring: false,
                                video: call.video
                            )
                        },
                        onDecline: { incomingCallViewModel.onAction(.dismiss) }
                    )
                    .padding(.horizontal, 16)
                    .padding(.top, 8)
                }
            }
    }

    /// 루트 내비게이션 컨테이너 — 셸(탭바 포함) 전체가 루트 콘텐츠라 상세 push 시 하단 탭까지 덮는다.
    /// NavigationStack은 iOS 16+라 iOS 15(iPhone 7)는 NavigationView로 동일 로직 폴백.
    @ViewBuilder private var navigationRoot: some View {
        if #available(iOS 16.0, *) {
            NavigationStack {
                shellContent
                    .navigationDestination(isPresented: showGroupDetail) { groupDetailDestination }
                    .navigationDestination(isPresented: $showAccountSettings) { accountSettingsDestination }
                    .navigationDestination(isPresented: showChatRoom) { chatRoomDestination }
                    .navigationDestination(isPresented: showAcceptedCall) { acceptedCallDestination }
                    .navigationDestination(isPresented: $showSettings) { settingsDestination }
            }
        } else {
            NavigationView {
                shellContent
                    .background(
                        NavigationLink(isActive: showGroupDetail) {
                            groupDetailDestination
                        } label: {
                            EmptyView()
                        }
                        .hidden()
                    )
                    .background(
                        NavigationLink(isActive: $showAccountSettings) {
                            accountSettingsDestination
                        } label: {
                            EmptyView()
                        }
                        .hidden()
                    )
                    .background(
                        NavigationLink(isActive: showChatRoom) {
                            chatRoomDestination
                        } label: {
                            EmptyView()
                        }
                        .hidden()
                    )
                    .background(
                        NavigationLink(isActive: showAcceptedCall) {
                            acceptedCallDestination
                        } label: {
                            EmptyView()
                        }
                        .hidden()
                    )
                    .background(
                        NavigationLink(isActive: $showSettings) {
                            settingsDestination
                        } label: {
                            EmptyView()
                        }
                        .hidden()
                    )
            }
            .navigationViewStyle(.stack)
        }
    }

    @ViewBuilder private var shellContent: some View {
        if theme.navStyle == .tabs {
            TabShellView(
                current: $current,
                showSettings: $showSettings,
                container: container,
                profile: profileViewModel.uiState.profile,
                notificationsViewModel: notificationsViewModel,
                chatViewModel: chatViewModel,
                onOpenGroup: { selectedGroupId = $0.id },
                onOpenChatRoom: { selectedChatRoom = $0 },
                onOpenAccountSettings: { showAccountSettings = true },
                onLogout: onLogout
            )
        } else {
            DrawerShellView(
                current: $current,
                showSettings: $showSettings,
                container: container,
                profile: profileViewModel.uiState.profile,
                notificationsViewModel: notificationsViewModel,
                chatViewModel: chatViewModel,
                onOpenGroup: { selectedGroupId = $0.id },
                onOpenChatRoom: { selectedChatRoom = $0 },
                onOpenAccountSettings: { showAccountSettings = true },
                onLogout: onLogout
            )
        }
    }

    @ViewBuilder private var groupDetailDestination: some View {
        if let groupId = selectedGroupId {
            GroupDetailView(groupId: groupId, container: container, chatViewModel: chatViewModel)
        }
    }

    @ViewBuilder private var chatRoomDestination: some View {
        if let room = selectedChatRoom {
            ChatRoomView(
                chatRoomId: room.chatRoomId,
                groupId: room.groupId,
                title: room.title,
                container: container,
                chatViewModel: chatViewModel
            )
        }
    }

    /// 세션 ProfileViewModel을 넘겨 저장 성공 시 프로필 탭/드로어 헤더가 갱신되게 한다
    private var accountSettingsDestination: some View {
        AccountSettingsView(container: container, profileViewModel: profileViewModel)
    }

    private var settingsDestination: some View {
        SGSettingsView(theme: theme)
    }

    /// pop(백 버튼/스와이프) 시 selectedGroupId를 nil로 되돌리는 브리지
    private var showGroupDetail: Binding<Bool> {
        Binding(
            get: { selectedGroupId != nil },
            set: { if !$0 { selectedGroupId = nil } }
        )
    }

    /// pop(백 버튼/스와이프) 시 selectedChatRoom을 nil로 되돌리는 브리지
    private var showChatRoom: Binding<Bool> {
        Binding(
            get: { selectedChatRoom != nil },
            set: { if !$0 { selectedChatRoom = nil } }
        )
    }

    @ViewBuilder private var acceptedCallDestination: some View {
        if let call = acceptedCall {
            CallView(chatRoomId: call.chatRoomId, title: call.title, ring: call.ring, video: call.video, container: container)
        }
    }

    /// pop(백 버튼/스와이프·끊기) 시 acceptedCall을 nil로 되돌리는 브리지
    private var showAcceptedCall: Binding<Bool> {
        Binding(
            get: { acceptedCall != nil },
            set: { if !$0 { acceptedCall = nil } }
        )
    }

    init(container: AppContainer, theme: SGThemeState, onLogout: @escaping () -> Void) {
        _profileViewModel = StateObject(wrappedValue: ProfileViewModel(getMyProfileUseCase: container.getMyProfileUseCase))
        _notificationsViewModel = StateObject(wrappedValue: NotificationsViewModel(
            getNotificationsPagingDataUseCase: container.getNotificationsPagingDataUseCase,
            getUnreadNotificationCountUseCase: container.getUnreadNotificationCountUseCase,
            markNotificationAsReadUseCase: container.markNotificationAsReadUseCase,
            markAllNotificationsAsReadUseCase: container.markAllNotificationsAsReadUseCase,
            observePersonalEventsUseCase: container.observePersonalEventsUseCase
        ))
        _chatViewModel = StateObject(wrappedValue: ChatViewModel(
            getGroupChatRoomsUseCase: container.getGroupChatRoomsUseCase,
            getDirectRoomsUseCase: container.getDirectRoomsUseCase,
            observePersonalEventsUseCase: container.observePersonalEventsUseCase
        ))
        _incomingCallViewModel = StateObject(wrappedValue: IncomingCallViewModel(
            observePersonalEventsUseCase: container.observePersonalEventsUseCase
        ))
        self.container = container
        self.theme = theme
        self.onLogout = onLogout
    }
}

/// 두 쉘이 공유하는 목적지 → 화면 매핑 — Compose DestinationContent 미러.
/// 상단바는 전 탭 기본 NavigationBar로 통일(사용자 지시) — 홈은 콜랩싱 헤더 때문에
/// 내비바(제목·툴바)는 셸이 루트 NavigationStack 위에서 목적지별로 구성한다.
struct DestinationView: View {
    let destination: SGDestination

    /// 화면이 자기 ViewModel을 만들 때 쓴다 — Compose LocalAppContainer 미러
    let container: AppContainer

    let profile: Profile?

    /// 셸 소유 세션 VM — 알림 화면이 종 뱃지와 같은 인스턴스를 쓴다(ProfileViewModel 주입 선례)
    let notificationsViewModel: NotificationsViewModel

    /// 셸 소유 세션 VM — 허브가 채팅 탭 뱃지와 같은 인스턴스를 쓴다
    let chatViewModel: ChatViewModel

    /// 그룹 상세 풀스크린 push — Compose onOpenGroupDetail 미러(MainShellView selectedGroupId)
    let onOpenGroup: (Group) -> Void

    /// 채팅방 풀스크린 push — Compose onOpenChatRoom 미러(MainShellView selectedChatRoom)
    let onOpenChatRoom: (ChatRoomRef) -> Void

    let onOpenSettings: () -> Void

    /// 계정 설정 풀스크린 push — Compose onOpenAccountSettings 미러(MainShellView showAccountSettings)
    let onOpenAccountSettings: () -> Void

    let onLogout: () -> Void

    var body: some View {
        switch destination {
        case .home:
            HomeView(container: container)
        case .groups:
            GroupsView(container: container, onOpenGroup: onOpenGroup)
        case .friends:
            FriendsView()
        case .chat:
            ChatView(viewModel: chatViewModel, onOpenChatRoom: onOpenChatRoom)
        case .notifications:
            NotificationsView(viewModel: notificationsViewModel)
        case .profile:
            ProfileView(
                profile: profile,
                onOpenAccountSettings: onOpenAccountSettings,
                onOpenSettings: onOpenSettings,
                onLogout: onLogout
            )
        }
    }
}
