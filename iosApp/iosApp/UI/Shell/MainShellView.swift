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

    let colors: SGColors

    let onLogout: () -> Void

    @StateObject private var profileModel: ProfileViewModel

    @State private var current: SGDestination = .home

    @State private var showSettings = false

    var body: some View {
        Group {
            if theme.navStyle == .tabs {
                TabShellView(
                    colors: colors,
                    current: $current,
                    showSettings: $showSettings,
                    profile: profileModel.uiState.profile,
                    onLogout: onLogout
                )
            } else {
                DrawerShellView(
                    colors: colors,
                    current: $current,
                    showSettings: $showSettings,
                    profile: profileModel.uiState.profile,
                    onLogout: onLogout
                )
            }
        }
        .sheet(isPresented: $showSettings) {
            SGSettingsView(theme: theme, colors: colors)
        }
        .onAppear { profileModel.load() }
    }

    init(container: AppContainer, theme: SGThemeState, colors: SGColors, onLogout: @escaping () -> Void) {
        _profileModel = StateObject(wrappedValue: ProfileViewModel(container: container))
        self.theme = theme
        self.colors = colors
        self.onLogout = onLogout
    }
}

/// 두 쉘이 공유하는 목적지 → 화면 매핑 — Compose DestinationContent 미러
struct DestinationView: View {
    let destination: SGDestination

    let colors: SGColors

    let profile: Profile?

    let onOpenSettings: () -> Void

    let onLogout: () -> Void

    var body: some View {
        switch destination {
        case .home:
            HomeView(colors: colors)
        case .groups:
            GroupsView(colors: colors)
        case .friends:
            FriendsView(colors: colors)
        case .chat:
            ChatView(colors: colors)
        case .notifications:
            NotificationsView(colors: colors)
        case .profile:
            ProfileView(colors: colors, profile: profile, onOpenSettings: onOpenSettings, onLogout: onLogout)
        }
    }
}
