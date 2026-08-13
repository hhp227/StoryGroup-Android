import SwiftUI
import Shared
// SwiftUI.Group(뷰)과 도메인 모델 Group의 동명 충돌 — 이 파일의 Group은 도메인 모델로 고정
import class Shared.Group

/// 기본 쉘: 웹 미러 헤더(linen+보더) + 하단 5탭 — Compose TabShell 미러
struct TabShellView: View {
    @Environment(\.sgColors) private var colors

    @Binding var current: SGDestination

    @Binding var showSettings: Bool

    /// 화면이 자기 ViewModel을 만들 때 쓴다 — Compose LocalAppContainer 미러
    let container: AppContainer

    let profile: Profile?

    /// 셸 뱃지 — MainShellView 소유 세션 VM(Compose sessionNotificationsViewModel/sessionChatViewModel 미러)
    @ObservedObject var notificationsViewModel: NotificationsViewModel

    @ObservedObject var chatViewModel: ChatViewModel

    /// 그룹 상세 풀스크린 push — MainShellView(루트 NavigationStack)로 위임
    let onOpenGroup: (Group) -> Void

    /// 채팅방 풀스크린 push — MainShellView(루트 NavigationStack)로 위임
    let onOpenChatRoom: (ChatRoomRef) -> Void

    /// 상세에서 나가기/삭제 후 복귀 — MainShellView groupsRefreshPending 드릴링(Compose TabShell.kt 미러)
    let groupsRefreshRequested: Bool

    let onGroupsRefreshHandled: () -> Void

    /// 계정 설정 풀스크린 push — MainShellView(루트 NavigationStack)로 위임
    let onOpenAccountSettings: () -> Void

    let onLogout: () -> Void

    /// 홈 헤더가 발행한 스크림 임계값 — 내비바 배경 수동 제어(자동 전환은 keep-alive ZStack에서 불가)
    @State private var homeBarScrimVisible = false

    var body: some View {
        VStack(spacing: 0) {
            // 내비바는 루트 NavigationStack의 것 하나 — 제목·툴바는 아래 modifier에서 current별 구성
            // switch로 갈아끼우면 뷰가 파괴돼 스크롤 위치가 초기화됨 — 전 목적지를 유지하고
            // 표시만 전환한다(네이티브 TabView의 탭 상태 유지 동작 미러, 커스텀 탭바라 직접 구현)
            ZStack {
                ForEach(SGDestination.allCases) { destination in
                    DestinationView(
                        destination: destination,
                        container: container,
                        profile: profile,
                        notificationsViewModel: notificationsViewModel,
                        chatViewModel: chatViewModel,
                        onOpenGroup: onOpenGroup,
                        onOpenChatRoom: onOpenChatRoom,
                        groupsRefreshRequested: groupsRefreshRequested,
                        onGroupsRefreshHandled: onGroupsRefreshHandled,
                        onOpenSettings: { showSettings = true },
                        onOpenAccountSettings: onOpenAccountSettings,
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
                                // 채팅 탭만 허브 미읽음 합계 뱃지 — Compose DestinationIcon 미러
                                .overlay(alignment: .topTrailing) {
                                    if destination == .chat {
                                        SGUnreadBadge(count: chatViewModel.uiState.totalUnread)
                                            .offset(x: 12, y: -6)
                                    }
                                }
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
                    Button(action: { current = .notifications }) {
                        Image(systemName: "bell.fill")
                            .overlay(alignment: .topTrailing) {
                                SGUnreadBadge(count: notificationsViewModel.uiState.unreadCount)
                                    .offset(x: 10, y: -8)
                            }
                    }
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
}
