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

    @State private var current: SGDestination = .home

    @State private var showSettings = false

    /// 풀스크린 push 대상 — Compose NavHost(GroupDetailRoute(groupId)) 미러. nil이 아니면 상세가 셸을 통째로 덮는다
    @State private var selectedGroupId: Int64? = nil

    var body: some View {
        navigationRoot
            .sheet(isPresented: $showSettings) {
                SGSettingsView(theme: theme)
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
                container: container,
                profile: profileViewModel.uiState.profile,
                onOpenGroup: { selectedGroupId = $0.id },
                onLogout: onLogout
            )
        } else {
            DrawerShellView(
                current: $current,
                showSettings: $showSettings,
                container: container,
                profile: profileViewModel.uiState.profile,
                onOpenGroup: { selectedGroupId = $0.id },
                onLogout: onLogout
            )
        }
    }

    @ViewBuilder private var groupDetailDestination: some View {
        if let groupId = selectedGroupId {
            GroupDetailView(groupId: groupId, container: container)
        }
    }

    /// pop(백 버튼/스와이프) 시 selectedGroupId를 nil로 되돌리는 브리지
    private var showGroupDetail: Binding<Bool> {
        Binding(
            get: { selectedGroupId != nil },
            set: { if !$0 { selectedGroupId = nil } }
        )
    }

    init(container: AppContainer, theme: SGThemeState, onLogout: @escaping () -> Void) {
        _profileViewModel = StateObject(wrappedValue: ProfileViewModel(container: container))
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

    /// 그룹 상세 풀스크린 push — Compose onOpenGroupDetail 미러(MainShellView selectedGroupId)
    let onOpenGroup: (Group) -> Void

    let onOpenSettings: () -> Void

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
            ChatView()
        case .notifications:
            NotificationsView()
        case .profile:
            ProfileView(profile: profile, onOpenSettings: onOpenSettings, onLogout: onLogout)
        }
    }
}
