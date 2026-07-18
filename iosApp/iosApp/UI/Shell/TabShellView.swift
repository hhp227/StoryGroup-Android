import SwiftUI
import Shared

/// 기본 쉘: 웹 미러 헤더(linen+보더) + 하단 5탭 — Compose TabShell 미러
struct TabShellView: View {
    @Environment(\.sgColors) private var colors

    @Binding var current: SGDestination

    @Binding var showSettings: Bool

    let profile: Profile?

    let homeViewModel: HomeViewModel

    let onLogout: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            SGHeader(
                title: current == .home ? "우리들의 이야기" : current.label,
                showsNotifications: current != .notifications,
                onNotifications: { current = .notifications },
                showsSettings: current == .profile,
                onSettings: { showSettings = true }
            )
            DestinationView(
                destination: current,
                profile: profile,
                homeViewModel: homeViewModel,
                onOpenSettings: { showSettings = true },
                onLogout: onLogout
            )
            Divider().background(colors.stoneBorder)
            HStack {
                ForEach(SGDestination.allCases.filter { $0.inTabs }) { destination in
                    Button(action: { current = destination }) {
                        VStack(spacing: 4) {
                            Image(systemName: destination.systemImage)
                                .font(.system(size: 20))
                            Text(destination.label)
                                .font(.system(size: 11, weight: .semibold))
                        }
                        .frame(maxWidth: .infinity)
                        .foregroundColor(destination == current ? colors.accent : colors.inkSoft)
                    }
                }
            }
            .padding(.top, 8)
            .padding(.bottom, 4)
            .background(colors.linen)
        }
        .background(colors.paper.ignoresSafeArea())
    }
}
