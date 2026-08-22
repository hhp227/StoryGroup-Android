import SwiftUI
import Shared
// SwiftUI.Group(뷰)과 도메인 모델 Group의 동명 충돌 — 이 파일의 Group은 도메인 모델로 고정
import class Shared.Group

/// 레거시 쉘: 구 앱 드로어(프로필 헤더 + 목적지 + 설정·로그아웃 보강) — Compose DrawerShell 미러
struct DrawerShellView: View {
    @Environment(\.sgColors) private var colors

    /// 탭 선택은 값으로 받는다 — 소유자는 MainShellView의 navigationViewModel.uiState.currentTab.
    /// 선택 자체는 onNavigationAction(.selectTab)으로 올려보낸다(Compose DrawerShell currentTab 미러)
    let current: SGDestination

    /// 화면 전환 의도의 단일 진입점 — MainShellView의 navigationViewModel.onAction(ConCafe 패턴)
    let onNavigationAction: (NavigationAction) -> Void

    /// 화면 간 결과 신호 — 그룹 탭 refresh 판정에 쓴다(Compose는 각 화면이 세션
    /// NavigationViewModel에서 직접 읽지만, iOS엔 세션 VM 저장소가 없어 셸이 드릴링한다)
    let pendingResults: Set<NavResult>

    /// 화면이 자기 ViewModel을 만들 때 쓴다 — Compose LocalAppContainer 미러
    let container: AppContainer

    let profile: Profile?

    /// 셸 뱃지 — MainShellView 소유 세션 VM(Compose sessionNotificationsViewModel/sessionChatViewModel 미러)
    @ObservedObject var notificationsViewModel: NotificationsViewModel

    @ObservedObject var chatViewModel: ChatViewModel

    /// 홈→게시글 상세→작성자 프로필 체인이 계정 설정 push에 쓴다 — 셸 소유 세션 VM 전달
    let profileViewModel: ProfileViewModel

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
                            profileViewModel: profileViewModel,
                            notificationsViewModel: notificationsViewModel,
                            chatViewModel: chatViewModel,
                            onOpenGroup: { onNavigationAction(.navigateToGroupDetail(groupId: $0.id)) },
                            onOpenChatRoom: { room in
                                onNavigationAction(.navigateToChatRoom(chatRoomId: room.chatRoomId, groupId: room.groupId, title: room.title))
                            },
                            onOpenUserProfile: { onNavigationAction(.navigateToUserProfile(userId: $0)) },
                        onOpenPost: { onNavigationAction(.navigateToPostDetail(groupId: $0, postId: $1)) },
                            // 상세에서 나가기/삭제 후 복귀 — 그룹 탭이 pendingResults를 소비해 목록을 다시 읽는다
                            groupsRefreshRequested: pendingResults.contains(.groupsChanged),
                            onGroupsRefreshHandled: { onNavigationAction(.consumeResult(.groupsChanged)) },
                            onOpenSettings: { onNavigationAction(.navigateToAppSettings) },
                            onOpenAccountSettings: { onNavigationAction(.navigateToAccountSettings) },
                            onOpenBlockedUsers: { onNavigationAction(.navigateToBlockedUsers) },
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
                    Button(action: { onNavigationAction(.navigateToSearch) }) { Image(systemName: "magnifyingglass") }
                }
                // 탭 쉘과 동일하게 내비바 우측에서도 알림 진입(알림 화면에서는 숨김)
                if current != .notifications {
                    Button(action: { onNavigationAction(.selectTab(destination: .notifications)) }) {
                        Image(systemName: "bell.fill")
                            .overlay(alignment: .topTrailing) {
                                SGUnreadBadge(count: notificationsViewModel.uiState.unreadCount)
                                    .offset(x: 10, y: -8)
                            }
                    }
                }
                if current == .profile {
                    Button(action: { onNavigationAction(.navigateToAppSettings) }) { Image(systemName: "gearshape.fill") }
                }
            }
        }
        .onPreferenceChange(NavigationBarScrimVisibleKey.self) { homeBarScrimVisible = $0 }
        // 홈은 헤더 사진 위 투명→스크롤 시 표시, 나머지 탭은 항상 표시(Compose SgTopBar 미러).
        // 단 드로어가 열려 있는 동안은 끈다 — UIKit 내비바는 SwiftUI 콘텐츠보다 항상 위에
        // 그려져서, 배경이 켜져 있으면 드로어 상단이 바에 가려진다(Compose 스크림이 톱바까지
        // 덮는 것의 근사)
        .navigationBarScrim(visible: !drawerOpen && (current == .home ? homeBarScrimVisible : true))
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
                        drawerRow(
                            destination.label,
                            destination.systemImage,
                            selected: destination == current,
                            badgeCount: drawerBadgeCount(for: destination)
                        ) {
                            onNavigationAction(.selectTab(destination: destination))
                            withAnimation(.easeIn(duration: 0.2)) { drawerOpen = false }
                        }
                    }
                    Divider().background(colors.stoneBorder).padding(.vertical, 8)
                    drawerRow("설정", "gearshape.fill", selected: false) {
                        withAnimation(.easeIn(duration: 0.2)) { drawerOpen = false }
                        onNavigationAction(.navigateToAppSettings)
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

    /// 드로어 목적지별 뱃지 수 — Compose DrawerShell badgeCount 미러
    private func drawerBadgeCount(for destination: SGDestination) -> Int64 {
        switch destination {
        case .chat: return chatViewModel.uiState.totalUnread
        case .notifications: return notificationsViewModel.uiState.unreadCount
        default: return 0
        }
    }

    private func drawerRow(
        _ label: String,
        _ systemImage: String,
        selected: Bool,
        badgeCount: Int64 = 0,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: systemImage)
                    .frame(width: 24)
                Text(label).font(.system(size: 15, weight: .semibold))
                Spacer()
                SGUnreadBadge(count: badgeCount)
            }
            .foregroundColor(selected ? colors.accent : colors.inkSoft)
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(selected ? colors.accentSoft : Color.clear)
            .clipShape(RoundedRectangle(cornerRadius: 10))
        }
    }
}
