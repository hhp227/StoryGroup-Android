import SwiftUI
import Shared

/// 기본 쉘: 웹 미러 헤더(linen+보더) + 하단 5탭 — Compose TabShell 미러
struct TabShellView: View {
    @Environment(\.sgColors) private var colors

    @Binding var current: SGDestination

    @Binding var showSettings: Bool

    let profile: Profile?

    let homeViewModel: HomeViewModel

    let groupsViewModel: GroupsViewModel

    /// 그룹 상세 풀스크린 push — MainShellView(루트 NavigationStack)로 위임
    let onOpenGroup: (Group) -> Void

    let onLogout: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            // 내비바는 루트 NavigationStack의 것 하나 — 제목·툴바는 아래 modifier에서 current별 구성
            // switch로 갈아끼우면 뷰가 파괴돼 스크롤 위치가 초기화됨 — 전 목적지를 유지하고
            // 표시만 전환한다(네이티브 TabView의 탭 상태 유지 동작 미러, 커스텀 탭바라 직접 구현)
            ZStack {
                ForEach(SGDestination.allCases) { destination in
                    DestinationView(
                        destination: destination,
                        profile: profile,
                        homeViewModel: homeViewModel,
                        groupsViewModel: groupsViewModel,
                        onOpenGroup: onOpenGroup,
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
        .navigationTitle(current == .home ? "우리들의 이야기" : current.label)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItemGroup(placement: .navigationBarTrailing) {
                if current == .home {
                    Button(action: { /* TODO: 검색 */ }) { Image(systemName: "magnifyingglass") }
                }
                // 알림은 탭에서 빠지고 내비바 종 아이콘으로 진입(알림 화면에서는 숨김)
                if current != .notifications {
                    Button(action: { current = .notifications }) { Image(systemName: "bell.fill") }
                }
                if current == .profile {
                    Button(action: { showSettings = true }) { Image(systemName: "gearshape.fill") }
                }
            }
        }
    }
}
