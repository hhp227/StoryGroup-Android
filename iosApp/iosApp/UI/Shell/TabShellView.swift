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
            // 홈은 화면이 콜랩싱 상단바(검색·알림 포함)를 직접 그린다 — 레거시 라운지 CollapsingToolbar 미러
            if current != .home {
                SGHeader(
                    title: current.label,
                    showsNotifications: current != .notifications,
                    onNotifications: { current = .notifications },
                    showsSettings: current == .profile,
                    onSettings: { showSettings = true }
                )
            }
            // switch로 갈아끼우면 뷰가 파괴돼 스크롤 위치가 초기화됨 — 전 목적지를 유지하고
            // 표시만 전환한다(네이티브 TabView의 탭 상태 유지 동작 미러, 커스텀 탭바라 직접 구현)
            ZStack {
                ForEach(SGDestination.allCases) { destination in
                    DestinationView(
                        destination: destination,
                        profile: profile,
                        homeViewModel: homeViewModel,
                        onOpenNotifications: { current = .notifications },
                        onOpenSettings: { showSettings = true },
                        onLogout: onLogout
                    )
                    .opacity(destination == current ? 1 : 0)
                    .allowsHitTesting(destination == current)
                }
            }
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
