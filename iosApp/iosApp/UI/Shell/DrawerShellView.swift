import SwiftUI
import Shared
// SwiftUI.Group(뷰)과 도메인 모델 Group의 동명 충돌 — 이 파일의 Group은 도메인 모델로 고정
import class Shared.Group

/// 레거시 쉘: 구 앱 드로어(프로필 헤더 + 목적지 + 설정·로그아웃 보강) — Compose DrawerShell 미러
struct DrawerShellView: View {
    @Environment(\.sgColors) private var colors

    @Binding var current: SGDestination

    @Binding var showSettings: Bool

    /// 화면이 자기 ViewModel을 만들 때 쓴다 — Compose LocalAppContainer 미러
    let container: AppContainer

    let profile: Profile?

    /// 그룹 상세 풀스크린 push — MainShellView(루트 NavigationStack)로 위임
    let onOpenGroup: (Group) -> Void

    /// 채팅방 풀스크린 push — MainShellView(루트 NavigationStack)로 위임
    let onOpenChatRoom: (ChatRoomRef) -> Void

    /// 계정 설정 풀스크린 push — MainShellView(루트 NavigationStack)로 위임
    let onOpenAccountSettings: () -> Void

    let onLogout: () -> Void

    @State private var drawerOpen = false

    /// 홈 헤더가 발행한 스크림 임계값 — 내비바 배경 수동 제어(자동 전환은 keep-alive ZStack에서 불가)
    @State private var homeBarScrimVisible = false

    var body: some View {
        ZStack(alignment: .leading) {
            VStack(spacing: 0) {
                // 내비바는 루트 NavigationStack의 것 하나 — 제목·툴바(햄버거 포함)는 아래 modifier에서 구성
                // 탭 쉘과 동일 — 목적지 전환 시 뷰를 유지해 스크롤 위치를 보존한다
                ZStack {
                    ForEach(SGDestination.allCases) { destination in
                        DestinationView(
                            destination: destination,
                            container: container,
                            profile: profile,
                            onOpenGroup: onOpenGroup,
                            onOpenChatRoom: onOpenChatRoom,
                            onOpenSettings: { showSettings = true },
                            onOpenAccountSettings: onOpenAccountSettings,
                            onLogout: onLogout
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
        .navigationTitle(current == .home ? "우리들의 이야기" : current.label)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarLeading) {
                Button(action: { withAnimation(.easeOut(duration: 0.2)) { drawerOpen = true } }) {
                    Image(systemName: "line.3.horizontal")
                }
            }
            ToolbarItemGroup(placement: .navigationBarTrailing) {
                if current == .home {
                    Button(action: { /* TODO: 검색 */ }) { Image(systemName: "magnifyingglass") }
                }
                // 탭 쉘과 동일하게 내비바 우측에서도 알림 진입(알림 화면에서는 숨김)
                if current != .notifications {
                    Button(action: { current = .notifications }) { Image(systemName: "bell.fill") }
                }
                if current == .profile {
                    Button(action: { showSettings = true }) { Image(systemName: "gearshape.fill") }
                }
            }
        }
        .onPreferenceChange(NavigationBarScrimVisibleKey.self) { homeBarScrimVisible = $0 }
        // 홈은 헤더 사진 위 투명→스크롤 시 표시, 나머지 탭은 항상 표시(Compose SgTopBar 미러)
        .navigationBarScrim(visible: current == .home ? homeBarScrimVisible : true)
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
