import SwiftUI
import Shared

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

    /// 그룹 상세 VM(그룹별 동적 생성) 팩토리에 필요 — Compose groupDetailViewModelFactory 미러
    private let container: AppContainer

    @StateObject private var profileViewModel: ProfileViewModel

    @StateObject private var homeViewModel: HomeViewModel

    @StateObject private var groupsViewModel: GroupsViewModel

    @State private var current: SGDestination = .home

    @State private var showSettings = false

    /// 풀스크린 push 대상 — Compose NavHost(GroupDetailRoute) 미러. nil이 아니면 상세가 셸을 통째로 덮는다
    @State private var selectedGroup: Group? = nil

    var body: some View {
        navigationRoot
            .sheet(isPresented: $showSettings) {
                SGSettingsView(theme: theme)
            }
            .onAppear {
                // 로그인 세션 진입 시마다 내 정보/홈 피드/그룹 목록 갱신(재로그인 포함) — Compose App.kt 미러
                profileViewModel.onAction(.load)
                homeViewModel.onAction(.refresh)
                groupsViewModel.onAction(.refresh)
            }
    }

    /// 루트 내비게이션 컨테이너 — 셸(탭바 포함) 전체가 루트 콘텐츠라 상세 push 시 하단 탭까지 덮는다.
    /// NavigationStack은 iOS 16+라 iOS 15(iPhone 7)는 NavigationView로 동일 로직 폴백.
    @ViewBuilder private var navigationRoot: some View {
        if #available(iOS 16.0, *) {
            NavigationStack {
                shellContent
                    .navigationDestination(isPresented: showGroupDetail) { groupDetailDestination }
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
            }
            .navigationViewStyle(.stack)
        }
    }

    @ViewBuilder private var shellContent: some View {
        if theme.navStyle == .tabs {
            TabShellView(
                current: $current,
                showSettings: $showSettings,
                profile: profileViewModel.uiState.profile,
                homeViewModel: homeViewModel,
                groupsViewModel: groupsViewModel,
                onOpenGroup: { selectedGroup = $0 },
                onLogout: onLogout
            )
        } else {
            DrawerShellView(
                current: $current,
                showSettings: $showSettings,
                profile: profileViewModel.uiState.profile,
                homeViewModel: homeViewModel,
                groupsViewModel: groupsViewModel,
                onOpenGroup: { selectedGroup = $0 },
                onLogout: onLogout
            )
        }
    }

    @ViewBuilder private var groupDetailDestination: some View {
        if let group = selectedGroup {
            GroupDetailView(group: group, factory: makeGroupDetailViewModel)
        }
    }

    /// pop(백 버튼/스와이프) 시 selectedGroup을 nil로 되돌리는 브리지
    private var showGroupDetail: Binding<Bool> {
        Binding(
            get: { selectedGroup != nil },
            set: { if !$0 { selectedGroup = nil } }
        )
    }

    private func makeGroupDetailViewModel(_ group: Group) -> GroupDetailViewModel {
        GroupDetailViewModel(container: container, group: group)
    }

    init(container: AppContainer, theme: SGThemeState, onLogout: @escaping () -> Void) {
        _profileViewModel = StateObject(wrappedValue: ProfileViewModel(container: container))
        _homeViewModel = StateObject(wrappedValue: HomeViewModel(container: container))
        _groupsViewModel = StateObject(wrappedValue: GroupsViewModel(container: container))
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

    let profile: Profile?

    let homeViewModel: HomeViewModel

    let groupsViewModel: GroupsViewModel

    /// 그룹 상세 풀스크린 push — Compose onOpenGroupDetail 미러(MainShellView selectedGroup)
    let onOpenGroup: (Group) -> Void

    let onOpenSettings: () -> Void

    let onLogout: () -> Void

    var body: some View {
        switch destination {
        case .home:
            HomeView(viewModel: homeViewModel)
        case .groups:
            GroupsView(viewModel: groupsViewModel, onOpenGroup: onOpenGroup)
        case .friends:
            FriendsView()
        case .chat:
            ChatView()
        case .notifications:
            NotificationsView()
        case .profile:
            ProfileView(profile: profile, onOpenSettings: onOpenSettings, onLogout: onLogout)
        }
    }
}
