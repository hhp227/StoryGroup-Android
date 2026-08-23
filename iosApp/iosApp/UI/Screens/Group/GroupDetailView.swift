import SwiftUI
import UIKit
import Paging
import Shared
// SwiftUI.Group(뷰)과 도메인 모델 Group의 동명 충돌 — 이 파일의 Group은 도메인 모델로 고정
import class Shared.Group

/// 공개 프로필 시트에서 고른 후속 push 대상 — 시트가 완전히 닫힌 뒤(onDismiss) 실행해야 유실되지 않는다
private enum ProfileFollowUp {
    case chatRoom(ChatRoomRef)
    case accountSettings
}

/// 그룹 상세 — 웹 /groups/[id]·Compose GroupDetailScreen 미러: 콜랩싱 커버(그라데이션 폴백+
/// 이름/설명/역할 칩)+5탭(소식/앨범/일정/멤버/설정). 상단바는 루트 NavigationStack의 기본 내비바.
/// groupId만 받아 VM이 스스로 로드한다(목록 페이징 전환으로 스냅샷 lookup 불가 — Compose 미러).
/// 계층은 Compose GroupDetailScreen과 1:1 — View=상태 소유(VM 선언), Content=구독+UI.
struct GroupDetailView: View {
    @StateObject private var viewModel: GroupDetailViewModel

    @StateObject private var groupFeedViewModel: GroupFeedViewModel

    @StateObject private var groupAlbumViewModel: GroupAlbumViewModel

    @StateObject private var groupMembersViewModel: GroupMembersViewModel

    @StateObject private var groupEventsViewModel: GroupEventsViewModel

    @StateObject private var groupSettingsViewModel: GroupSettingsViewModel

    /// 글쓰기 화면(CreatePostView)의 VM 생성에 쓰인다
    private let container: AppContainer

    /// DM 채팅방 push에 넘길 허브 세션 VM(셸 소유) — 진입/이탈 신호용
    private let chatViewModel: ChatViewModel

    /// 앱 설정 push용 테마 상태(셸 소유) — SGSettingsView가 요구한다
    private let theme: SGThemeState

    /// 유저 설정 행+계정 설정 push용 세션 VM(셸 소유) — AccountSettingsView 선례
    private let profileViewModel: ProfileViewModel

    /// 설정 탭에서 삭제/나가기 성공 — 화면이 스스로 닫히고(pop) 그룹 목록을 갱신해야 한다(Compose GroupDetailScreen onGroupClosed 미러)
    private let onGroupClosed: () -> Void

    /// 그룹 정보 수정 저장 성공 — 목록 카드의 이름·커버 갱신 신호(pop은 하지 않는다)
    private let onGroupUpdated: () -> Void

    var body: some View {
        GroupDetailContent(
            viewModel: viewModel,
            groupFeedViewModel: groupFeedViewModel,
            groupAlbumViewModel: groupAlbumViewModel,
            groupMembersViewModel: groupMembersViewModel,
            groupEventsViewModel: groupEventsViewModel,
            groupSettingsViewModel: groupSettingsViewModel,
            container: container,
            chatViewModel: chatViewModel,
            theme: theme,
            profileViewModel: profileViewModel,
            onGroupClosed: onGroupClosed,
            onGroupUpdated: onGroupUpdated
        )
    }

    init(
        groupId: Int64,
        container: AppContainer,
        chatViewModel: ChatViewModel,
        theme: SGThemeState,
        profileViewModel: ProfileViewModel,
        onGroupClosed: @escaping () -> Void,
        onGroupUpdated: @escaping () -> Void
    ) {
        _viewModel = StateObject(wrappedValue: GroupDetailViewModel(
            groupId: groupId,
            getGroupUseCase: container.getGroupUseCase,
            getGroupDefaultChatRoomUseCase: container.getGroupDefaultChatRoomUseCase
        ))
        _groupFeedViewModel = StateObject(wrappedValue: GroupFeedViewModel(
            groupId: groupId,
            getGroupPostsPagingDataUseCase: container.getGroupPostsPagingDataUseCase,
            observePostUpdatesUseCase: container.observePostUpdatesUseCase,
            observeUserBlocksUseCase: container.observeUserBlocksUseCase,
            observePostDeletionsUseCase: container.observePostDeletionsUseCase,
            togglePostLikeUseCase: container.togglePostLikeUseCase
        ))
        _groupAlbumViewModel = StateObject(wrappedValue: GroupAlbumViewModel(
            groupId: groupId,
            getGroupPhotosPagingDataUseCase: container.getGroupPhotosPagingDataUseCase
        ))
        _groupMembersViewModel = StateObject(wrappedValue: GroupMembersViewModel(
            groupId: groupId,
            getGroupMembersUseCase: container.getGroupMembersUseCase,
            getJoinRequestsUseCase: container.getJoinRequestsUseCase,
            approveJoinRequestUseCase: container.approveJoinRequestUseCase,
            rejectJoinRequestUseCase: container.rejectJoinRequestUseCase,
            createGroupInviteUseCase: container.createGroupInviteUseCase,
            getBlockedUsersUseCase: container.getBlockedUsersUseCase,
            getCurrentUserIdUseCase: container.getCurrentUserIdUseCase
        ))
        _groupEventsViewModel = StateObject(wrappedValue: GroupEventsViewModel(
            groupId: groupId,
            getGroupEventsUseCase: container.getGroupEventsUseCase,
            getEventDetailUseCase: container.getEventDetailUseCase,
            createEventUseCase: container.createEventUseCase,
            deleteEventUseCase: container.deleteEventUseCase,
            rsvpEventUseCase: container.rsvpEventUseCase,
            cancelEventRsvpUseCase: container.cancelEventRsvpUseCase,
            getCurrentUserIdUseCase: container.getCurrentUserIdUseCase
        ))
        _groupSettingsViewModel = StateObject(wrappedValue: GroupSettingsViewModel(
            groupId: groupId,
            getGroupUseCase: container.getGroupUseCase,
            deleteGroupUseCase: container.deleteGroupUseCase,
            leaveGroupUseCase: container.leaveGroupUseCase
        ))
        self.container = container
        self.chatViewModel = chatViewModel
        self.theme = theme
        self.profileViewModel = profileViewModel
        self.onGroupClosed = onGroupClosed
        self.onGroupUpdated = onGroupUpdated
    }
}

private struct GroupDetailContent: View {
    @ObservedObject var viewModel: GroupDetailViewModel

    @ObservedObject var groupFeedViewModel: GroupFeedViewModel

    @ObservedObject var groupAlbumViewModel: GroupAlbumViewModel

    @ObservedObject var groupMembersViewModel: GroupMembersViewModel

    @ObservedObject var groupEventsViewModel: GroupEventsViewModel

    @ObservedObject var groupSettingsViewModel: GroupSettingsViewModel

    let container: AppContainer

    /// DM 채팅방 push에 넘길 허브 세션 VM(셸 소유) — 진입/이탈 신호용
    let chatViewModel: ChatViewModel

    /// 앱 설정 push용 테마 상태(셸 소유) — SGSettingsView가 요구한다
    let theme: SGThemeState

    /// 유저 설정 행+계정 설정 push용 세션 VM(셸 소유) — AccountSettingsView 선례
    @ObservedObject var profileViewModel: ProfileViewModel

    /// 설정 탭에서 삭제/나가기 성공 — 화면이 스스로 닫히고(pop) 그룹 목록을 갱신해야 한다(Compose GroupDetailScreen onGroupClosed 미러)
    let onGroupClosed: () -> Void

    /// 그룹 정보 수정 저장 성공 — 목록 카드의 이름·커버 갱신 신호(pop은 하지 않는다)
    let onGroupUpdated: () -> Void

    /// Compose collectAsLazyPagingItems 미러 — 뷰 수명 동안 페이징 스트림 구독을 유지한다
    @StateObject private var lazyPagingItems: LazyPagingItems<Post>

    /// 앨범 탭 페이징 스트림 — 피드와 동일 관용구(Compose photoLazyPagingItems 미러)
    @StateObject private var photoLazyPagingItems: LazyPagingItems<GroupPhoto>

    /// 레거시 R.array.tab_name(소식/앨범/맴버/설정)에 일정 추가 — Compose tabs 미러
    private static let tabs = ["소식", "앨범", "일정", "멤버", "설정"]

    /// 선택 탭 — Compose pagerState.currentPage 미러(iOS는 스와이프 없이 탭 터치만 — 플랫폼 관용 예외)
    @State private var selectedTab = 0

    @Environment(\.sgColors) private var colors

    /// 커버가 발행한 스크림 임계값 — 내비바 배경 수동 제어(자동 전환은 keep-alive ZStack에서 불가)
    @State private var barScrimVisible = false

    /// 내비바 아래 노출 커버 높이(탭바 제외) — HomeView headerHeight와 동일 규칙(Compose 170dp - 툴바 56dp)
    /// 스크롤 변위 측정용 좌표계 이름 — ScrollView에 건다
    private static let scrollSpace = "groupDetailScroll"

    private let headerHeight: CGFloat = 114

    /// 커버 하단에 겹치는 탭바 높이 — Compose CollapsingTabRowHeight(48dp) 미러
    private static let tabBarHeight: CGFloat = 48

    /// 레거시 TabLayout tabTextColor(#FFE1E3E5) — 이미지 위 비선택 탭 텍스트
    private static let tabTextOnImage = Color(red: 225 / 255, green: 227 / 255, blue: 229 / 255)

    /// 그룹 글쓰기 풀스크린 push — Compose CreatePostRoute(groupId) 미러
    @State private var showCreatePost = false

    /// 모더레이터 초대코드 다이얼로그 — Compose GroupDetailScreen showInviteDialog 미러
    @State private var showInviteDialog = false

    /// 멤버 탭 셀 탭 → 공개 프로필 시트 — Compose UserProfileRoute 다이얼로그 미러(DM은 프로필의 버튼 몫)
    @State private var selectedProfileUserId: Int64? = nil

    /// 프로필 시트의 후속 이동(DM 채팅방/본인 계정 설정) — 시트 dismiss 완료 후 push한다
    @State private var profileFollowUp: ProfileFollowUp? = nil

    /// push할 채팅방 — Compose ChatRoomRoute 미러(상단바 채팅 버튼=그룹 기본 방, 멤버 탭 DM 공용)
    @State private var pushedChatRoom: ChatRoomRef?

    /// 공유 시트 대상 — 카드 공유 버튼이 채우면 ActivityShareSheet가 뜬다(Compose postShareText 미러)
    @State private var shareItem: ShareItem?

    /// 설정 탭 풀스크린 push 4종 — Compose GroupEditRoute/AccountSettingsRoute/AppSettingsRoute/GroupReportsRoute 미러
    @State private var showGroupEdit = false

    @State private var showAccountSettings = false

    @State private var showAppSettings = false

    @State private var showGroupReports = false

    /// 공유 문구 — 레거시 share 미러(앱 소개+웹 주소, Compose APP_SHARE_TEXT 미러)
    private static let appShareText = "StoryGroup — 그룹과 함께하는 이야기\n\(AppLinks.shared.BASE_URL)"

    /// 상세 안에서 채팅방·글쓰기를 push — NavigationStack은 iOS 16+라 iOS 15는 숨김 NavigationLink 폴백(셸 미러)
    var body: some View {
        if #available(iOS 16.0, *) {
            core
                .navigationDestination(isPresented: showChatRoom) { chatRoomDestination }
                .navigationDestination(isPresented: $showCreatePost) { createPostDestination }
                .navigationDestination(isPresented: $showGroupEdit) { groupEditDestination }
                .navigationDestination(isPresented: $showAccountSettings) { accountSettingsDestination }
                .navigationDestination(isPresented: $showAppSettings) { appSettingsDestination }
                .navigationDestination(isPresented: $showGroupReports) { groupReportsDestination }
                .sheet(isPresented: showProfile, onDismiss: runProfileFollowUp) { profileDestination }
        } else {
            core
                .background(
                    NavigationLink(isActive: showChatRoom) {
                        chatRoomDestination
                    } label: {
                        EmptyView()
                    }
                    .hidden()
                )
                .background(
                    NavigationLink(isActive: $showCreatePost) {
                        createPostDestination
                    } label: {
                        EmptyView()
                    }
                    .hidden()
                )
                .background(
                    NavigationLink(isActive: $showGroupEdit) {
                        groupEditDestination
                    } label: {
                        EmptyView()
                    }
                    .hidden()
                )
                .background(
                    NavigationLink(isActive: $showAccountSettings) {
                        accountSettingsDestination
                    } label: {
                        EmptyView()
                    }
                    .hidden()
                )
                .background(
                    NavigationLink(isActive: $showAppSettings) {
                        appSettingsDestination
                    } label: {
                        EmptyView()
                    }
                    .hidden()
                )
                .background(
                    NavigationLink(isActive: $showGroupReports) {
                        groupReportsDestination
                    } label: {
                        EmptyView()
                    }
                    .hidden()
                )
                .sheet(isPresented: showProfile, onDismiss: runProfileFollowUp) { profileDestination }
        }
    }

    private var core: some View {
        GeometryReader { outer in
            ScrollView {
                VStack(spacing: 12) {
                    cover(topInset: outer.safeAreaInsets.top)
                    tabContent
                }
                .padding(.bottom, 16)
            }
            .background(colors.paper)
            // 커버가 투명한 내비바·상태바 뒤까지 깔리도록
            .ignoresSafeArea(edges: .top)
            // 스크롤 변위 측정 기준 — 전역 좌표는 로딩 중 레이아웃이 정착하는 동안 값이 튀어
            // 커버가 떨린다(진입 깜빡임). 이 좌표계는 스크롤뷰 프레임이 원점이라 그 잡음에 면역이다
            .coordinateSpace(name: Self.scrollSpace)
            // 당겨서 새로고침 — 그룹 정보(멤버/가입 신청 포함)와 피드+앨범을 함께 갱신한다.
            // Compose GroupDetailScreen 미러 — ScrollView의 시스템 스피너는 iOS 16+에서 표시(15에선 무동작)
            .refreshable {
                viewModel.onAction(.refresh)
                groupMembersViewModel.onAction(.refresh)
                groupEventsViewModel.onAction(.refresh)
                // Compose GroupDetailScreen onRefresh 미러 — 프레젠터가 직접 paging 스트림을 무효화한다
                // (event 경유 트리거가 없어졌으므로 awaitRefresh 전에 refresh()를 명시적으로 태운다)
                lazyPagingItems.refresh()
                photoLazyPagingItems.refresh()
                await lazyPagingItems.awaitRefresh()
                await photoLazyPagingItems.awaitRefresh()
            }
        }
        // 하단 인디케이터 탭바가 스크롤로 접힌 뒤 내비바 아래 고정되는 사본(핀 탭바 미러)
        .overlay(alignment: .top) {
            if barScrimVisible {
                tabBar(onImage: false)
            }
        }
        // 레거시 fragment_group_detail.xml의 fab(bottom|end) 미러 — 소식 탭에서만(레거시 isTabPositionZero 미러)
        .overlay(alignment: .bottomTrailing) {
            if selectedTab == 0 {
                SGFab(action: { showCreatePost = true }).padding(16)
            }
        }
        .overlay {
            if showInviteDialog {
                InviteDialog(
                    invite: groupMembersViewModel.uiState.createdInvite,
                    isLoading: groupMembersViewModel.uiState.isCreatingInvite,
                    error: groupMembersViewModel.uiState.inviteError,
                    onDismiss: {
                        showInviteDialog = false
                        // 닫을 때 결과를 비워 다음에 열면 다시 생성 폼부터 시작한다
                        groupMembersViewModel.onAction(.dismissInvite)
                    },
                    onCreate: { groupMembersViewModel.onAction(.createInvite(maxUses: $0, expiresInDays: $1)) }
                )
            }
        }
        // 로드 전엔 빈 제목 — 커버 그라데이션(groupId 기반)은 즉시 그려진다
        .navigationTitle(viewModel.uiState.group?.name ?? "")
        .navigationBarTitleDisplayMode(.inline)
        // 그룹 채팅방 진입 — 상단바 액션(레거시 group.xml action_chat·웹 커버 "채팅" 버튼 미러).
        // 기본 방 id는 상세 로드에 실려 온다 — 로드 전/실패 시엔 버튼이 숨는다(Compose 미러)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                if let chatRoomId = viewModel.uiState.defaultChatRoomId {
                    Button {
                        // 방 제목은 허브(그룹 방 목록)와 동일하게 그룹명을 쓴다
                        pushedChatRoom = ChatRoomRef(
                            chatRoomId: chatRoomId,
                            groupId: viewModel.groupId,
                            title: viewModel.uiState.group?.name ?? ""
                        )
                    } label: {
                        Image(systemName: "bubble.left.fill")
                    }
                }
            }
        }
        // 설정 탭 일회성 이벤트 — 삭제/나가기 성공 시 화면 닫기(저장 갱신은 groupEditDestination 클로저 경로)
        .onReceive(groupSettingsViewModel.event) { event in
            switch event {
            case .closed: onGroupClosed()
            }
        }
        .onPreferenceChange(NavigationBarScrimVisibleKey.self) { barScrimVisible = $0 }
        .navigationBarScrim(visible: barScrimVisible)
        // 상세 진입 시 신선화 — 목록에서 받은 그룹으로 먼저 그리고 최신화한다(Compose LaunchedEffect(viewModel) 미러)
        .onAppear {
            viewModel.onAction(.refresh)
            groupMembersViewModel.onAction(.refresh)
        }
        // 카드 공유 버튼 — iOS 15 타깃이라 ShareLink(16+) 대신 UIActivityViewController(Compose postShareText 미러)
        .sheet(item: $shareItem) { item in
            ActivityShareSheet(text: item.text)
        }
        .alert("좋아요 처리 실패", isPresented: Binding(
            get: { groupFeedViewModel.uiState.likeError != nil },
            set: { if !$0 { groupFeedViewModel.onAction(.dismissLikeError) } }
        )) {
            Button("확인", role: .cancel) {}
        } message: {
            Text(groupFeedViewModel.uiState.likeError ?? "")
        }
    }

    /// 커버 배너 — group.image 있으면 실사진, 없으면 웹 GroupCover 그라데이션 폴백. 패럴럭스+stretchy는 HomeView 미러.
    /// 이미지는 탭바 영역까지 깔리고 탭바가 커버 하단에 겹친다(레거시 TabLayout 투명 배경 미러) —
    /// 탭바는 패럴럭스 밖에 둬 스크롤과 같은 속도로 접힌다(Compose TabRow bottom 핀 미러)
    private func cover(topInset: CGFloat) -> some View {
        let total = headerHeight + Self.tabBarHeight + topInset
        return GeometryReader { geo in
            // 스크롤 변위 — rest에서 정확히 0, 아래로 스크롤하면 음수, 당기면 양수.
            //
            // ⚠️`+ topInset`을 빼지 말 것. 이 좌표계의 원점은 화면 최상단이 아니라 "안전영역 상단"인데
            // 커버는 .ignoresSafeArea(edges: .top)로 화면 최상단부터 깔린다. 그래서 rest에서 minY가
            // -topInset으로 나오고, 안 더하면 패럴럭스가 커버를 topInset/2만큼 아래로 밀어 상단에
            // 여백이 생긴다(실제로 겪은 회귀).
            //
            // ⚠️전역 좌표(.global)로 되돌리지도 말 것. rest는 0으로 맞지만 로딩 중 레이아웃이
            // 정착하는 동안 값이 튀어 커버가 떨린다. onAppear에서 기준값을 한 번 잡아 빼던 보정도
            // 안 된다 — 그 값이 잘못 잡히면 rest 변위가 남아 커버 콘텐츠가 clipped에 잘리고
            // 그룹 설명이 사라진다. 셋 다 실제로 겪었다.
            let raw = geo.frame(in: .named(Self.scrollSpace)).minY + topInset
            let minY = raw
            let stretch = max(0, minY)
            ZStack(alignment: .bottomLeading) {
                // 바탕 그라데이션은 항상 깔아두고 사진을 그 위에 덧그린다.
                // 분기로 바꿔 끼우면 상세 로드 전후·재진입마다 바탕이 통째로 갈아엎어져 깜빡인다
                // (AsyncImage는 자체 캐시가 없어 뷰가 다시 만들어질 때마다 다시 받는다)
                groupCoverGradient(groupId: viewModel.groupId, colors: colors)
                if let imageUrlString = viewModel.uiState.group?.image, let url = URL(string: imageUrlString) {
                    AsyncImage(url: url) { phase in
                        if case .success(let image) = phase {
                            image.resizable().scaledToFill()
                        } else {
                            // 로드 전·실패는 비워 둔다 — 아래 그라데이션이 그대로 보인다
                            Color.clear
                        }
                    }
                }
                // 웹 커버 하단 스크림(0.05→0.62) — 흰 텍스트 대비 확보
                LinearGradient(
                    gradient: Gradient(colors: [Color.black.opacity(0.05), Color.black.opacity(0.62)]),
                    startPoint: .top,
                    endPoint: .bottom
                )
                // 그룹명/설명은 투명 탭바 위에서 끝나야 안 가린다
                coverOverlay.padding(16).padding(.bottom, Self.tabBarHeight)
            }
            .frame(width: geo.size.width, height: total + stretch)
            .offset(y: minY < 0 ? -minY * 0.5 : 0)
            .frame(width: geo.size.width, height: total + stretch, alignment: .top)
            .clipped()
            .overlay(alignment: .bottom) {
                tabBar(onImage: true)
            }
            .offset(y: -stretch)
            // 피드 아이템이 내비바 영역에 닿는 시점부터 바 배경을 켠다 — rest 보정값이 아니라
            // 화면 기하(raw ≤ -headerHeight)로 판정(홈과 동일, 셸별 오프셋 차이 방지)
            .preference(key: NavigationBarScrimVisibleKey.self, value: raw <= -headerHeight)
        }
        .frame(height: total)
    }

    /// 웹 커버 스크림 위 그룹명/설명/역할 칩 미러 — 로드 전(nil)엔 자리만 비워 둔다
    @ViewBuilder private var coverOverlay: some View {
        if let group = viewModel.uiState.group {
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 8) {
                    Text(group.name)
                        .font(.title3.bold())
                        .foregroundColor(.white)
                        .lineLimit(1)
                    RoleChip(role: group.myRole)
                }
                if let description = group.description_, !description.isEmpty {
                    Text(description)
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.88))
                        .lineLimit(2)
                }
            }
        }
    }

    /// 탭바 아래 콘텐츠 — 선택된 탭에 맞는 뷰로 전환한다(Compose GroupDetailScreen page switch 미러)
    @ViewBuilder private var tabContent: some View {
        switch selectedTab {
        case 0: feedTab
        case 1: GroupAlbumTab(
            photoItems: photoLazyPagingItems,
            groupId: viewModel.groupId,
            container: container,
            chatViewModel: chatViewModel,
            profileViewModel: profileViewModel
        )
        case 2: GroupEventsTab(viewModel: groupEventsViewModel, canModerate: viewModel.uiState.canModerate)
        case 3: membersTab
        default: GroupSettingsTab(
            viewModel: groupSettingsViewModel,
            profile: profileViewModel.uiState.profile,
            onOpenGroupEdit: { showGroupEdit = true },
            onOpenAccountSettings: { showAccountSettings = true },
            onOpenAppSettings: { showAppSettings = true },
            onOpenGroupReports: { showGroupReports = true },
            onShareApp: { shareItem = ShareItem(text: Self.appShareText) }
        )
        }
    }

    /// 하단 인디케이터 탭바 — Compose TabRow 미러. 커버 위 인라인(onImage=투명 배경·흰 텍스트,
    /// 레거시 TabLayout 미러)으로 흐르다가 barScrimVisible이면 오버레이 사본(paper 배경·잉크
    /// 텍스트)이 내비바 아래 고정된다(핀 탭바 미러 — 단일 ScrollView라 stickyHeader가 없다)
    private func tabBar(onImage: Bool) -> some View {
        HStack(spacing: 0) {
            ForEach(Array(Self.tabs.enumerated()), id: \.offset) { index, title in
                Button { selectedTab = index } label: {
                    Text(title)
                        .font(.subheadline.weight(selectedTab == index ? .bold : .regular))
                        .foregroundColor(
                            selectedTab == index
                                ? (onImage ? .white : colors.ink)
                                : (onImage ? Self.tabTextOnImage : colors.inkFaint)
                        )
                        // 글자는 48pt 안에서 세로 중앙 — 칸 전체가 탭 히트 영역(M2 Tab 미러)
                        .frame(maxWidth: .infinity)
                        .frame(height: Self.tabBarHeight)
                        // 인디케이터는 Compose TabRow처럼 바닥에 겹쳐 그린다 — VStack으로 쌓으면
                        // 인디케이터+간격(8pt)만큼 글자가 위로 밀려 세로 중앙에서 벗어난다
                        .overlay(alignment: .bottom) {
                            Rectangle()
                                .fill(selectedTab == index ? colors.accent : Color.clear)
                                .frame(height: 2)
                        }
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .frame(maxWidth: .infinity)
            }
        }
        .background(onImage ? Color.clear : colors.paper)
    }

    /// 소식 탭 — 기존 피드 목록 그대로(인박스·초대코드·멤버 스트립은 멤버 탭으로 이동, Compose GroupFeedTab 미러).
    /// 피드는 Paging LoadState — 다음 페이지 트리거는 라이브러리가 담당.
    /// (라이브러리 LoadState.Error의 원인 에러는 internal이라 문구는 고정 메시지 사용)
    @ViewBuilder private var feedTab: some View {
        let refreshState = lazyPagingItems.loadState.refresh
        let appendState = lazyPagingItems.loadState.append

        if let error = viewModel.uiState.error {
            VStack(spacing: 8) {
                Text(error).font(.subheadline).foregroundColor(colors.rust)
                Button("다시 시도") { viewModel.onAction(.refresh) }
                    .font(.subheadline)
                    .foregroundColor(colors.accent)
            }
            .padding(.vertical, 16)
        }
        if lazyPagingItems.itemCount == 0, refreshState is LoadState.Loading {
            ProgressView().padding(.vertical, 48)
        } else if lazyPagingItems.itemCount == 0, refreshState is LoadState.Error {
            VStack(spacing: 8) {
                Text("피드를 불러오지 못했습니다.").font(.subheadline).foregroundColor(colors.rust)
                Button("다시 시도") { lazyPagingItems.retry() }
                    .font(.subheadline)
                    .foregroundColor(colors.accent)
            }
            .padding(.vertical, 48)
        } else if lazyPagingItems.itemCount == 0 {
            SGEmptyState(title: "아직 이야기가 없습니다", subtitle: "첫 이야기를 남겨보세요.")
                .padding(.vertical, 48)
        } else {
            LazyVStack(spacing: 12) {
                ForEach(lazyPagingItems, key: { AnyHashable($0.id) }) { post in
                    if let post {
                        // 카드 탭 → 게시글 상세 push. 삭제·차단은 상세가 알림만 흘리고,
                        // 이 VM이 스냅샷에서 그 글을 걷어낸다(전체 재조회 없음).
                        // 차단한 사람은 멤버 탭에서도 빠져야 해서 재진입 시 .refresh가 다시 걸린다
                        NavigationLink {
                            PostDetailView(
                                container: container,
                                groupId: post.groupId,
                                postId: post.id,
                                chatViewModel: chatViewModel,
                                profileViewModel: profileViewModel
                            )
                        } label: {
                            SGPostCard(
                                post: post,
                                onToggleLike: { groupFeedViewModel.onAction(.toggleLike(post)) },
                                onShare: { shareItem = ShareItem(text: postShareText(post)) }
                            )
                        }
                        .buttonStyle(.plain)
                    }
                }
                SGPagingFooter(
                    error: appendState is LoadState.Error ? "피드를 더 불러오지 못했습니다." : nil,
                    isLoadingMore: appendState is LoadState.Loading,
                    onRetry: { lazyPagingItems.retry() }
                )
            }
            .padding(.horizontal, 16)
        }
    }

    /// 모더레이터용 가입 신청 인박스 — 웹 GroupMemberList의 "가입 신청 N건" 섹션(Compose JoinRequestInbox 미러)
    private var joinRequestInbox: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("가입 신청 \(groupMembersViewModel.uiState.joinRequests.count)건")
                .font(.subheadline.bold())
                .foregroundColor(colors.ink)
            if let actionError = groupMembersViewModel.uiState.actionError {
                Text(actionError)
                    .font(.caption)
                    .foregroundColor(colors.rust)
            }
            ForEach(groupMembersViewModel.uiState.joinRequests, id: \.userId) { request in
                joinRequestCard(request)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func joinRequestCard(_ request: GroupJoinRequest) -> some View {
        // VM이 한 건씩만 처리하므로 처리 중엔 모든 행의 버튼을 잠근다
        let enabled = groupMembersViewModel.uiState.processingRequestUserId == nil
        let isProcessing = groupMembersViewModel.uiState.processingRequestUserId == request.userId
        return SGCard {
            HStack(spacing: 8) {
                SGAvatar(name: request.name, imageUrl: request.profileImg)
                VStack(alignment: .leading, spacing: 2) {
                    Text(request.name)
                        .font(.subheadline.bold())
                        .foregroundColor(colors.ink)
                        .lineLimit(1)
                    Text("\(TimeFormats.relative(request.requestedAt)) 신청")
                        .font(.caption2)
                        .foregroundColor(colors.inkFaint)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Button {
                    groupMembersViewModel.onAction(.approveJoinRequest(userId: request.userId))
                } label: {
                    HStack(spacing: 6) {
                        if isProcessing {
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: colors.inkFaint))
                                .scaleEffect(0.7)
                        }
                        Text("승인").font(.subheadline.bold())
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(
                        RoundedRectangle(cornerRadius: colors.radiusButton ?? 20, style: .continuous)
                            .fill(enabled ? colors.accent : colors.accentSoft)
                    )
                    .foregroundColor(enabled ? colors.onAccent : colors.inkFaint)
                }
                .disabled(!enabled)
                Button {
                    groupMembersViewModel.onAction(.rejectJoinRequest(userId: request.userId))
                } label: {
                    Text("거절")
                        .font(.subheadline)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                        .overlay(
                            RoundedRectangle(cornerRadius: colors.radiusButton ?? 20, style: .continuous)
                                .stroke(colors.stoneBorder, lineWidth: 1)
                        )
                        .foregroundColor(enabled ? colors.ink : colors.inkFaint)
                }
                .disabled(!enabled)
            }
            .padding(12)
        }
    }

    /// Compose GroupDetailScreen의 "초대코드 만들기" OutlinedButton 미러
    private var inviteButton: some View {
        Button(action: { showInviteDialog = true }) {
            Text("초대코드 만들기")
                .font(.subheadline.bold())
                .foregroundColor(colors.accent)
                .frame(maxWidth: .infinity)
                .frame(height: 40)
                .overlay(
                    RoundedRectangle(cornerRadius: colors.radiusButton ?? 20, style: .continuous)
                        .stroke(colors.stoneBorder, lineWidth: 1)
                )
        }
        .buttonStyle(.plain)
    }

    /// 멤버 탭 — 인박스+초대코드(멤버 관리 성격이라 여기 모음)+4열 그리드(레거시 MemberFragment·Compose GroupMembersTab 미러)
    @ViewBuilder private var membersTab: some View {
        VStack(alignment: .leading, spacing: 12) {
            // 멤버 로드 실패 — 탭 자체가 자기 로드를 소유하므로 여기서 재시도를 준다
            // (Compose GroupDetailScreen.kt:GroupMembersTab의 "members-error" 아이템 미러)
            if let error = groupMembersViewModel.uiState.error {
                VStack(spacing: 8) {
                    Text(error).font(.subheadline).foregroundColor(colors.rust)
                    Button("다시 시도") { groupMembersViewModel.onAction(.refresh) }
                        .font(.subheadline)
                        .foregroundColor(colors.accent)
                }
                .frame(maxWidth: .infinity)
            }
            // (Compose GroupDetailScreen.kt:GroupMembersTab의 "members-loading" 아이템 미러)
            if groupMembersViewModel.uiState.isLoading, groupMembersViewModel.uiState.members.isEmpty {
                ProgressView()
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 48)
            }
            if !groupMembersViewModel.uiState.joinRequests.isEmpty {
                joinRequestInbox
            }
            if viewModel.uiState.canModerate {
                inviteButton
            }
            Text("멤버 \(groupMembersViewModel.uiState.visibleMembers.count)")
                .font(.subheadline.bold())
                .foregroundColor(colors.ink)
            LazyVGrid(columns: [
                GridItem(.flexible(), spacing: 12, alignment: .top),
                GridItem(.flexible(), spacing: 12, alignment: .top),
                GridItem(.flexible(), spacing: 12, alignment: .top),
                GridItem(.flexible(), spacing: 12, alignment: .top)
            ], spacing: 16) {
                ForEach(groupMembersViewModel.uiState.visibleMembers, id: \.userId) { member in
                    // 본인 포함 전원 탭 가능 — 프로필 시트 진입(본인=프로필 수정, DM 버튼은 타인에게만 보인다)
                    Button(action: { selectedProfileUserId = member.userId }) {
                        VStack(spacing: 4) {
                            SGAvatar(name: member.name, imageUrl: member.profileImg)
                            Text(member.name)
                                .font(.caption2)
                                .foregroundColor(colors.inkSoft)
                                .lineLimit(1)
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(.horizontal, 16)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// pop(백 버튼/스와이프) 시 pushedChatRoom을 nil로 되돌리는 브리지(MainShellView 미러)
    private var showChatRoom: Binding<Bool> {
        Binding(
            get: { pushedChatRoom != nil },
            set: { if !$0 { pushedChatRoom = nil } }
        )
    }

    @ViewBuilder private var chatRoomDestination: some View {
        if let room = pushedChatRoom {
            ChatRoomView(
                chatRoomId: room.chatRoomId,
                groupId: room.groupId,
                title: room.title,
                container: container,
                chatViewModel: chatViewModel
            )
        }
    }

    private var createPostDestination: some View {
        // 성공 시 그룹 피드·앨범을 첫 페이지부터 다시 읽는다(Compose GroupDetailScreen
        // LaunchedEffect(refreshRequested) 미러 — 프레젠터가 직접 paging 스트림을 무효화한다)
        CreatePostView(container: container, groupId: viewModel.groupId) {
            lazyPagingItems.refresh()
            photoLazyPagingItems.refresh()
        }
    }

    /// 그룹 정보 수정 — 저장 성공 시 pop+상세·설정 탭 refresh+목록 갱신 신호(Compose NavResult.GroupUpdated
    /// pendingResults 미러)
    private var groupEditDestination: some View {
        GroupEditView(groupId: viewModel.groupId, container: container) {
            showGroupEdit = false
            viewModel.onAction(.refresh)
            groupSettingsViewModel.onAction(.refresh)
            onGroupUpdated()
        }
    }

    /// 세션 ProfileViewModel을 넘겨 저장 성공 시 프로필 탭/드로어 헤더가 갱신되게 한다(셸 선례)
    private var accountSettingsDestination: some View {
        AccountSettingsView(container: container, profileViewModel: profileViewModel)
    }

    private var appSettingsDestination: some View {
        SGSettingsView(theme: theme)
    }

    /// 신고함(모더레이터) — 신고된 게시글 탭 시 상세 push 체인에 세션 VM 2종이 필요해 전달한다
    private var groupReportsDestination: some View {
        GroupReportsView(
            groupId: viewModel.groupId,
            container: container,
            chatViewModel: chatViewModel,
            profileViewModel: profileViewModel
        )
    }

    /// 멤버 탭 셀 탭 → 공개 프로필 시트(검색·친구 탭과 같은 진입 규칙)
    @ViewBuilder private var profileDestination: some View {
        if let userId = selectedProfileUserId {
            UserProfileView(
                userId: userId,
                container: container,
                onOpenChatRoom: { room in
                    profileFollowUp = .chatRoom(room)
                    selectedProfileUserId = nil
                },
                onOpenAccountSettings: {
                    profileFollowUp = .accountSettings
                    selectedProfileUserId = nil
                }
            )
        }
    }

    /// 프로필 시트 dismiss 완료 후 후속 push 실행 — 드래그로 닫으면 followUp이 nil이라 아무 일 없다
    private func runProfileFollowUp() {
        switch profileFollowUp {
        case .chatRoom(let room): pushedChatRoom = room
        case .accountSettings: showAccountSettings = true
        case nil: break
        }
        profileFollowUp = nil
    }

    /// 시트를 닫으면(X·드래그) selectedProfileUserId를 nil로 되돌리는 브리지(MainShellView 선례)
    private var showProfile: Binding<Bool> {
        Binding(
            get: { selectedProfileUserId != nil },
            set: { if !$0 { selectedProfileUserId = nil } }
        )
    }

    init(
        viewModel: GroupDetailViewModel,
        groupFeedViewModel: GroupFeedViewModel,
        groupAlbumViewModel: GroupAlbumViewModel,
        groupMembersViewModel: GroupMembersViewModel,
        groupEventsViewModel: GroupEventsViewModel,
        groupSettingsViewModel: GroupSettingsViewModel,
        container: AppContainer,
        chatViewModel: ChatViewModel,
        theme: SGThemeState,
        profileViewModel: ProfileViewModel,
        onGroupClosed: @escaping () -> Void,
        onGroupUpdated: @escaping () -> Void
    ) {
        // Compose와 동일: 상태에서 pagingData만 뽑아낸 스트림을 collectAsLazyPagingItems로 수집
        // (Kotlin: feedViewModel.uiState.map { it.pagingData }.distinctUntilChanged())
        let pagingDataPublisher = groupFeedViewModel.$uiState.map { $0.pagingData }.removeDuplicates { $0 === $1 }
        // 앨범 탭 — 피드와 동일 관용구, 상태에서 photosPagingData만 뽑아낸 스트림을 수집
        let photosPublisher = groupAlbumViewModel.$uiState.map { $0.photosPagingData }.removeDuplicates { $0 === $1 }

        self.viewModel = viewModel
        self.groupFeedViewModel = groupFeedViewModel
        self.groupAlbumViewModel = groupAlbumViewModel
        self.groupMembersViewModel = groupMembersViewModel
        self.groupEventsViewModel = groupEventsViewModel
        self.groupSettingsViewModel = groupSettingsViewModel
        self.container = container
        self.chatViewModel = chatViewModel
        self.theme = theme
        self.profileViewModel = profileViewModel
        self.onGroupClosed = onGroupClosed
        self.onGroupUpdated = onGroupUpdated
        _lazyPagingItems = StateObject(wrappedValue: pagingDataPublisher.collectAsLazyPagingItems())
        _photoLazyPagingItems = StateObject(wrappedValue: photosPublisher.collectAsLazyPagingItems())
    }
}

/// 모더레이터용 초대코드 다이얼로그 — 생성 폼과 결과(코드+복사)를 한 다이얼로그에서 전환한다(Compose InviteDialog 미러).
/// 웹엔 생성 UI가 없어 앱이 자체 디자인 — 계약은 백엔드 CreateInviteRequest(@Min 1, @Max 365) 미러.
/// DiscoverGroupsView의 다이얼로그처럼 반투명 배경+중앙 카드로 직접 구현(iOS 15 공통)
private struct InviteDialog: View {
    let invite: GroupInvite?

    let isLoading: Bool

    let error: String?

    let onDismiss: () -> Void

    let onCreate: (Int?, Int?) -> Void

    @Environment(\.sgColors) private var colors

    @State private var maxUsesText = ""

    @State private var expiresInDaysText = ""

    @State private var copied = false

    /// 서버 검증(@Min 1, @Max 365)을 입력 단계에서 막는다 — 빈칸은 무제한/무기한
    private var canCreate: Bool {
        let maxUsesValid = maxUsesText.isEmpty || (Int(maxUsesText) ?? 0) >= 1
        let daysValid = expiresInDaysText.isEmpty || (1...365).contains(Int(expiresInDaysText) ?? 0)
        return maxUsesValid && daysValid
    }

    var body: some View {
        ZStack {
            Color.black.opacity(0.35)
                .ignoresSafeArea()
                .onTapGesture(perform: onDismiss)
            SGCard {
                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        Text("초대코드 만들기")
                            .font(.headline)
                            .foregroundColor(colors.ink)
                        Spacer()
                        Button(action: onDismiss) {
                            Image(systemName: "xmark").foregroundColor(colors.inkSoft)
                        }
                    }
                    if let invite = invite {
                        result(invite)
                    } else {
                        form
                    }
                }
                .padding(16)
            }
            .padding(24)
        }
    }

    @ViewBuilder private var form: some View {
        Text("코드를 전달받은 사람은 승인 없이 바로 가입됩니다.")
            .font(.caption)
            .foregroundColor(colors.inkSoft)
        SGTextField(label: "최대 사용 횟수 (비우면 무제한)", text: $maxUsesText, keyboard: .numberPad)
            .onChange(of: maxUsesText) { maxUsesText = $0.filter(\.isNumber) }
        SGTextField(label: "유효 기간(일, 비우면 무기한)", text: $expiresInDaysText, keyboard: .numberPad)
            .onChange(of: expiresInDaysText) { expiresInDaysText = $0.filter(\.isNumber) }
        if let error = error {
            Text(error).font(.caption).foregroundColor(colors.rust)
        }
        SGPrimaryButton(
            title: "만들기",
            enabled: canCreate,
            isLoading: isLoading,
            action: { onCreate(Int(maxUsesText), Int(expiresInDaysText)) }
        )
    }

    @ViewBuilder private func result(_ invite: GroupInvite) -> some View {
        Text(invite.code)
            .font(.title2.bold())
            .kerning(4)
            .foregroundColor(colors.ink)
            .frame(maxWidth: .infinity)
        Text(limitLabel(invite))
            .font(.caption)
            .foregroundColor(colors.inkSoft)
            .frame(maxWidth: .infinity)
        SGPrimaryButton(title: copied ? "복사됨" : "코드 복사") {
            UIPasteboard.general.string = invite.code
            copied = true
        }
    }

    private func limitLabel(_ invite: GroupInvite) -> String {
        var parts: [String] = []

        if let maxUses = invite.maxUses { parts.append("최대 \(maxUses)회 사용") }
        // 서버 ISO-8601 원문에서 날짜만 잘라 보여준다
        if let expiresAt = invite.expiresAt { parts.append("\(expiresAt.prefix(10))까지 유효") }
        return parts.isEmpty ? "사용 제한 없음" : parts.joined(separator: " · ")
    }
}
