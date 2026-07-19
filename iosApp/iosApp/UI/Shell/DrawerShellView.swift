import SwiftUI
import Shared

/// 레거시 쉘: 구 앱 드로어(프로필 헤더 + 목적지 + 설정·로그아웃 보강) — Compose DrawerShell 미러
struct DrawerShellView: View {
    @Environment(\.sgColors) private var colors

    @Binding var current: SGDestination

    @Binding var showSettings: Bool

    let profile: Profile?

    let homeViewModel: HomeViewModel

    let onLogout: () -> Void

    @State private var drawerOpen = false

    var body: some View {
        ZStack(alignment: .leading) {
            VStack(spacing: 0) {
                // 홈은 화면이 콜랩싱 상단바를 직접 그린다(햄버거는 homeMenuAction으로 전달)
                if current != .home {
                    SGHeader(
                        title: current.label,
                        leadingIcon: "line.3.horizontal",
                        onLeading: { withAnimation(.easeOut(duration: 0.2)) { drawerOpen = true } },
                        showsNotifications: current != .notifications,
                        onNotifications: { current = .notifications }
                    )
                }
                // 탭 쉘과 동일 — 목적지 전환 시 뷰를 유지해 스크롤 위치를 보존한다
                ZStack {
                    ForEach(SGDestination.allCases) { destination in
                        DestinationView(
                            destination: destination,
                            profile: profile,
                            homeViewModel: homeViewModel,
                            onOpenNotifications: { current = .notifications },
                            onOpenSettings: { showSettings = true },
                            onLogout: onLogout,
                            homeMenuAction: { withAnimation(.easeOut(duration: 0.2)) { drawerOpen = true } }
                        )
                        .opacity(destination == current ? 1 : 0)
                        .allowsHitTesting(destination == current)
                    }
                }
            }
            .background(colors.paper.ignoresSafeArea())
            if drawerOpen {
                Color.black.opacity(0.35)
                    .ignoresSafeArea()
                    .onTapGesture { withAnimation(.easeIn(duration: 0.2)) { drawerOpen = false } }
                drawerContent
                    .transition(.move(edge: .leading))
            }
        }
    }

    // 구 앱 nav_header_main 미러: 프로필 헤더 + 목적지 + 설정·로그아웃
    private var drawerContent: some View {
        VStack(alignment: .leading, spacing: 0) {
            VStack(alignment: .leading, spacing: 8) {
                Spacer().frame(height: 40)
                SGAvatar(name: profile?.name ?? "?", size: 64, background: colors.accentSoft, foreground: colors.accent)
                Text(profile?.name ?? "불러오는 중...").font(.headline).foregroundColor(colors.onAccent)
                Text(profile?.email ?? "").font(.caption).foregroundColor(colors.onAccent)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(colors.accent)
            ScrollView {
                VStack(alignment: .leading, spacing: 4) {
                    ForEach(SGDestination.allCases) { destination in
                        drawerRow(destination.label, destination.systemImage, selected: destination == current) {
                            current = destination
                            withAnimation(.easeIn(duration: 0.2)) { drawerOpen = false }
                        }
                    }
                    Divider().background(colors.stoneBorder).padding(.vertical, 8)
                    drawerRow("설정", "gearshape.fill", selected: false) {
                        withAnimation(.easeIn(duration: 0.2)) { drawerOpen = false }
                        showSettings = true
                    }
                    drawerRow("로그아웃", "rectangle.portrait.and.arrow.right", selected: false) {
                        withAnimation(.easeIn(duration: 0.2)) { drawerOpen = false }
                        onLogout()
                    }
                }
                .padding(12)
            }
        }
        .frame(width: 280)
        .background(colors.linen.ignoresSafeArea())
    }

    private func drawerRow(_ label: String, _ systemImage: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: systemImage)
                    .frame(width: 24)
                Text(label).font(.system(size: 15, weight: .semibold))
                Spacer()
            }
            .foregroundColor(selected ? colors.accent : colors.inkSoft)
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(selected ? colors.accentSoft : Color.clear)
            .clipShape(RoundedRectangle(cornerRadius: 10))
        }
    }
}
