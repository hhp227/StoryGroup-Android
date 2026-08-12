import SwiftUI
import UIKit
import Paging
import Shared
// SwiftUI.Group(뷰)과 도메인 모델 Group의 동명 충돌 — 이 파일의 Group은 도메인 모델로 고정
import class Shared.Group

/// 그룹 상세 — 웹 /groups/[id]·Compose GroupDetailScreen 미러: 콜랩싱 커버(그라데이션 폴백+
/// 이름/설명/역할 칩)+5탭(소식/앨범/일정/멤버/설정). 상단바는 루트 NavigationStack의 기본 내비바.
/// groupId만 받아 VM이 스스로 로드한다(목록 페이징 전환으로 스냅샷 lookup 불가 — Compose 미러).
/// 계층은 Compose GroupDetailScreen과 1:1 — View=상태 소유(VM 선언), Content=구독+UI.
struct GroupDetailView: View {
    @StateObject private var viewModel: GroupDetailViewModel

    /// 글쓰기 화면(CreatePostView)의 VM 생성에 쓰인다
    private let container: AppContainer

    /// DM 채팅방 push에 넘길 허브 세션 VM(셸 소유) — 진입/이탈 신호용
    private let chatViewModel: ChatViewModel

    var body: some View {
        GroupDetailContent(viewModel: viewModel, container: container, chatViewModel: chatViewModel)
    }

    init(groupId: Int64, container: AppContainer, chatViewModel: ChatViewModel) {
        _viewModel = StateObject(wrappedValue: GroupDetailViewModel(
            groupId: groupId,
            getGroupUseCase: container.getGroupUseCase,
            getGroupMembersUseCase: container.getGroupMembersUseCase,
            getJoinRequestsUseCase: container.getJoinRequestsUseCase,
            approveJoinRequestUseCase: container.approveJoinRequestUseCase,
            rejectJoinRequestUseCase: container.rejectJoinRequestUseCase,
            createGroupInviteUseCase: container.createGroupInviteUseCase,
            openDirectRoomUseCase: container.openDirectRoomUseCase,
            getGroupDefaultChatRoomUseCase: container.getGroupDefaultChatRoomUseCase,
            getBlockedUsersUseCase: container.getBlockedUsersUseCase,
            getCurrentUserIdUseCase: container.getCurrentUserIdUseCase,
            getGroupPostsPagingDataUseCase: container.getGroupPostsPagingDataUseCase,
            getGroupPhotosPagingDataUseCase: container.getGroupPhotosPagingDataUseCase,
            observePostUpdatesUseCase: container.observePostUpdatesUseCase,
            observeUserBlocksUseCase: container.observeUserBlocksUseCase,
            observePostDeletionsUseCase: container.observePostDeletionsUseCase,
            togglePostLikeUseCase: container.togglePostLikeUseCase
        ))
        self.container = container
        self.chatViewModel = chatViewModel
    }
}

private struct GroupDetailContent: View {
    @ObservedObject var viewModel: GroupDetailViewModel

    let container: AppContainer

    /// DM 채팅방 push에 넘길 허브 세션 VM(셸 소유) — 진입/이탈 신호용
    let chatViewModel: ChatViewModel

    /// Compose collectAsLazyPagingItems 미러 — 뷰 수명 동안 페이징 스트림 구독을 유지한다
    @StateObject private var lazyPagingItems: LazyPagingItems<Post>

    /// 앨범 탭 페이징 스트림 — 피드와 동일 관용구(Compose photoLazyPagingItems 미러)
    @StateObject private var photoLazyPagingItems: LazyPagingItems<GroupPhoto>

    /// 레거시 R.array.tab_name(소식/앨범/맴버/설정)에 일정 추가 — Compose tabs 미러
    private static let tabs = ["소식", "앨범", "일정", "멤버", "설정"]

    /// 선택 탭 — Compose pagerState.currentPage 미러(iOS는 스와이프 없이 탭 터치만 — 플랫폼 관용 예외)
    @State private var selectedTab = 0

    @Environment(\.sgColors) private var colors

    /// 첫 레이아웃 시점 커버의 global minY — 스크롤 오프셋은 이 기준의 상대값(HomeView와 동일한 인셋 보정)
    @State private var headerRestMinY: CGFloat?

    /// 커버가 발행한 스크림 임계값 — 내비바 배경 수동 제어(자동 전환은 keep-alive ZStack에서 불가)
    @State private var barScrimVisible = false

    /// 내비바 아래 노출 커버 높이 — HomeView headerHeight와 동일 규칙(Compose 170dp - 툴바 56dp)
    private let headerHeight: CGFloat = 114

    /// 그룹 글쓰기 풀스크린 push — Compose CreatePostRoute(groupId) 미러
    @State private var showCreatePost = false

    /// 모더레이터 초대코드 다이얼로그 — Compose GroupDetailScreen showInviteDialog 미러
    @State private var showInviteDialog = false

    /// DM 확인 다이얼로그 대상 — 멤버 탭에서 타인을 탭하면 채워진다(Compose dmTargetMember 미러)
    @State private var dmTargetMember: GroupMember?

    /// push할 채팅방 — Compose ChatRoomRoute 미러(상단바 채팅 버튼=그룹 기본 방, 멤버 탭 DM 공용)
    @State private var pushedChatRoom: ChatRoomRef?

    /// 공유 시트 대상 — 카드 공유 버튼이 채우면 ActivityShareSheet가 뜬다(Compose postShareText 미러)
    @State private var shareItem: ShareItem?

    /// 상세 안에서 채팅방·글쓰기를 push — NavigationStack은 iOS 16+라 iOS 15는 숨김 NavigationLink 폴백(셸 미러)
    var body: some View {
        if #available(iOS 16.0, *) {
            core
                .navigationDestination(isPresented: showChatRoom) { chatRoomDestination }
                .navigationDestination(isPresented: $showCreatePost) { createPostDestination }
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
        }
    }

    private var core: some View {
        GeometryReader { outer in
            ScrollView {
                VStack(spacing: 12) {
                    cover(topInset: outer.safeAreaInsets.top)
                    tabBar
                    tabContent
                }
                .padding(.bottom, 16)
            }
            .background(colors.paper)
            // 커버가 투명한 내비바·상태바 뒤까지 깔리도록
            .ignoresSafeArea(edges: .top)
            // 당겨서 새로고침 — 그룹 정보(멤버/가입 신청 포함)와 피드+앨범을 함께 갱신한다.
            // Compose GroupDetailScreen 미러 — ScrollView의 시스템 스피너는 iOS 16+에서 표시(15에선 무동작)
            .refreshable {
                viewModel.onAction(.refresh)
                viewModel.onAction(.refreshFeed)
                await lazyPagingItems.awaitRefresh()
                await photoLazyPagingItems.awaitRefresh()
            }
        }
        // 하단 인디케이터 탭바가 스크롤로 접힌 뒤 내비바 아래 고정되는 사본(핀 탭바 미러)
        .overlay(alignment: .top) {
            if barScrimVisible {
                tabBar
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
                    invite: viewModel.uiState.createdInvite,
                    isLoading: viewModel.uiState.isCreatingInvite,
                    error: viewModel.uiState.inviteError,
                    onDismiss: {
                        showInviteDialog = false
                        // 닫을 때 결과를 비워 다음에 열면 다시 생성 폼부터 시작한다
                        viewModel.onAction(.dismissInvite)
                    },
                    onCreate: { viewModel.onAction(.createInvite(maxUses: $0, expiresInDays: $1)) }
                )
            }
        }
        .overlay {
            if let member = dmTargetMember {
                DmConfirmDialog(
                    memberName: member.name,
                    isLoading: viewModel.uiState.isOpeningDm,
                    error: viewModel.uiState.dmError,
                    onDismiss: {
                        dmTargetMember = nil
                        viewModel.onAction(.dismissDm)
                    },
                    onConfirm: { viewModel.onAction(.openDm(userId: member.userId, userName: member.name)) }
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
        // VM의 일회성 갱신 이벤트 — 프레젠터 refresh()가 활성 PagingSource를 무효화해
        // 같은 스트림이 새 세대(첫 페이지)를 방출한다(홈 피드와 동일 패턴)
        .onReceive(viewModel.event) { event in
            switch event {
            case .refreshFeed:
                lazyPagingItems.refresh()
                photoLazyPagingItems.refresh()
            case .dmOpened(let chatRoomId, let title):
                dmTargetMember = nil
                // DM 방은 groupId 없이 접근한다(/api/dm 경로) — 제목은 상대 이름
                pushedChatRoom = ChatRoomRef(chatRoomId: chatRoomId, groupId: nil, title: title)
            }
        }
        .onPreferenceChange(NavigationBarScrimVisibleKey.self) { barScrimVisible = $0 }
        .navigationBarScrim(visible: barScrimVisible)
        // 상세 진입 시 신선화 — 목록에서 받은 그룹으로 먼저 그리고 최신화한다
        .onAppear { viewModel.onAction(.refresh) }
        // 카드 공유 버튼 — iOS 15 타깃이라 ShareLink(16+) 대신 UIActivityViewController(Compose postShareText 미러)
        .sheet(item: $shareItem) { item in
            ActivityShareSheet(text: item.text)
        }
        .alert("좋아요 처리 실패", isPresented: Binding(
            get: { viewModel.uiState.likeError != nil },
            set: { if !$0 { viewModel.onAction(.dismissLikeError) } }
        )) {
            Button("확인", role: .cancel) {}
        } message: {
            Text(viewModel.uiState.likeError ?? "")
        }
    }

    /// 커버 배너 — group.image 있으면 실사진, 없으면 웹 GroupCover 그라데이션 폴백. 패럴럭스+stretchy는 HomeView 미러
    private func cover(topInset: CGFloat) -> some View {
        let total = headerHeight + topInset
        return GeometryReader { geo in
            let raw = geo.frame(in: .global).minY
            let minY = raw - (headerRestMinY ?? raw)
            let stretch = max(0, minY)
            ZStack(alignment: .bottomLeading) {
                if let imageUrlString = viewModel.uiState.group?.image, let url = URL(string: imageUrlString) {
                    AsyncImage(url: url) { phase in
                        if case .success(let image) = phase {
                            image.resizable().scaledToFill()
                        } else {
                            groupCoverGradient(groupId: viewModel.groupId, colors: colors)
                        }
                    }
                } else {
                    groupCoverGradient(groupId: viewModel.groupId, colors: colors)
                }
                // 웹 커버 하단 스크림(0.05→0.62) — 흰 텍스트 대비 확보
                LinearGradient(
                    gradient: Gradient(colors: [Color.black.opacity(0.05), Color.black.opacity(0.62)]),
                    startPoint: .top,
                    endPoint: .bottom
                )
                coverOverlay.padding(16)
            }
            .frame(width: geo.size.width, height: total + stretch)
            .offset(y: minY < 0 ? -minY * 0.5 : 0)
            .frame(width: geo.size.width, height: total + stretch, alignment: .top)
            .clipped()
            .offset(y: -stretch)
            .onAppear {
                if headerRestMinY == nil { headerRestMinY = raw }
            }
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
        case 1: GroupAlbumTab(photoItems: photoLazyPagingItems, groupId: viewModel.groupId, container: container)
        case 2: SGEmptyState(title: "일정", subtitle: "준비 중입니다.").padding(.vertical, 48)
        case 3: membersTab
        default: SGEmptyState(title: "설정", subtitle: "준비 중입니다.").padding(.vertical, 48)
        }
    }

    /// 하단 인디케이터 탭바 — Compose TabRow 미러. 인라인으로 흐르다가 barScrimVisible이면
    /// 오버레이 사본이 내비바 아래 고정된다(핀 탭바 미러 — 단일 ScrollView라 stickyHeader가 없다)
    private var tabBar: some View {
        HStack(spacing: 0) {
            ForEach(Array(Self.tabs.enumerated()), id: \.offset) { index, title in
                Button { selectedTab = index } label: {
                    VStack(spacing: 6) {
                        Text(title)
                            .font(.subheadline.weight(selectedTab == index ? .bold : .regular))
                            .foregroundColor(selectedTab == index ? colors.ink : colors.inkFaint)
                        Rectangle()
                            .fill(selectedTab == index ? colors.accent : Color.clear)
                            .frame(height: 2)
                    }
                    .padding(.top, 10)
                }
                .buttonStyle(.plain)
                .frame(maxWidth: .infinity)
            }
        }
        .background(colors.paper)
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
                                postId: post.id
                            )
                        } label: {
                            SGPostCard(
                                post: post,
                                onToggleLike: { viewModel.onAction(.toggleLike(post)) },
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
            Text("가입 신청 \(viewModel.uiState.joinRequests.count)건")
                .font(.subheadline.bold())
                .foregroundColor(colors.ink)
            if let actionError = viewModel.uiState.actionError {
                Text(actionError)
                    .font(.caption)
                    .foregroundColor(colors.rust)
            }
            ForEach(viewModel.uiState.joinRequests, id: \.userId) { request in
                joinRequestCard(request)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func joinRequestCard(_ request: GroupJoinRequest) -> some View {
        // VM이 한 건씩만 처리하므로 처리 중엔 모든 행의 버튼을 잠근다
        let enabled = viewModel.uiState.processingRequestUserId == nil
        let isProcessing = viewModel.uiState.processingRequestUserId == request.userId
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
                    viewModel.onAction(.approveJoinRequest(userId: request.userId))
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
                    viewModel.onAction(.rejectJoinRequest(userId: request.userId))
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
            if !viewModel.uiState.joinRequests.isEmpty {
                joinRequestInbox
            }
            if viewModel.uiState.canModerate {
                inviteButton
            }
            Text("멤버 \(viewModel.uiState.visibleMembers.count)")
                .font(.subheadline.bold())
                .foregroundColor(colors.ink)
            LazyVGrid(columns: [
                GridItem(.flexible(), spacing: 12, alignment: .top),
                GridItem(.flexible(), spacing: 12, alignment: .top),
                GridItem(.flexible(), spacing: 12, alignment: .top),
                GridItem(.flexible(), spacing: 12, alignment: .top)
            ], spacing: 16) {
                ForEach(viewModel.uiState.visibleMembers, id: \.userId) { member in
                    Button(action: { dmTargetMember = member }) {
                        VStack(spacing: 4) {
                            SGAvatar(name: member.name, imageUrl: member.profileImg)
                            Text(member.name)
                                .font(.caption2)
                                .foregroundColor(colors.inkSoft)
                                .lineLimit(1)
                        }
                    }
                    .buttonStyle(.plain)
                    // 본인은 DM 대상이 아니라 탭도 막는다(서버도 self-DM은 400)
                    .disabled(member.userId == viewModel.uiState.myUserId)
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
        // 성공 시 그룹 피드를 첫 페이지부터 다시 읽는다 — Compose GroupDetailScreen refreshRequested 미러
        CreatePostView(container: container, groupId: viewModel.groupId) {
            viewModel.onAction(.refreshFeed)
        }
    }

    init(viewModel: GroupDetailViewModel, container: AppContainer, chatViewModel: ChatViewModel) {
        // Compose와 동일: 상태에서 pagingData만 뽑아낸 스트림을 collectAsLazyPagingItems로 수집
        // (Kotlin: viewModel.uiState.map { it.pagingData }.distinctUntilChanged())
        let pagingDataPublisher = viewModel.$uiState.map { $0.pagingData }.removeDuplicates { $0 === $1 }
        // 앨범 탭 — 피드와 동일 관용구, 상태에서 photosPagingData만 뽑아낸 스트림을 수집
        let photosPublisher = viewModel.$uiState.map { $0.photosPagingData }.removeDuplicates { $0 === $1 }

        self.viewModel = viewModel
        self.container = container
        self.chatViewModel = chatViewModel
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

/// 멤버 탭 → 1:1 DM 확인 다이얼로그 — Compose DmConfirmDialog 미러(InviteDialog와 같은
/// 반투명 배경+중앙 카드, iOS 15 공통). 성공 시 dmOpened 이벤트로 채팅방으로 push된다
private struct DmConfirmDialog: View {
    let memberName: String

    let isLoading: Bool

    let error: String?

    let onDismiss: () -> Void

    let onConfirm: () -> Void

    @Environment(\.sgColors) private var colors

    var body: some View {
        ZStack {
            Color.black.opacity(0.35)
                .ignoresSafeArea()
                .onTapGesture(perform: onDismiss)
            SGCard {
                VStack(alignment: .leading, spacing: 12) {
                    Text("1:1 DM")
                        .font(.headline)
                        .foregroundColor(colors.ink)
                    Text("\(memberName)님과 1:1 DM을 시작할까요?")
                        .font(.subheadline)
                        .foregroundColor(colors.ink)
                    if let error = error {
                        Text(error).font(.caption).foregroundColor(colors.rust)
                    }
                    HStack(spacing: 8) {
                        Button(action: onDismiss) {
                            Text("취소")
                                .font(.subheadline)
                                .frame(maxWidth: .infinity)
                                // SGPrimaryButton과 같은 높이로 나란히 맞춘다
                                .frame(height: 48)
                                .overlay(
                                    RoundedRectangle(cornerRadius: colors.radiusButton ?? 20, style: .continuous)
                                        .stroke(colors.stoneBorder, lineWidth: 1)
                                )
                                .foregroundColor(colors.ink)
                        }
                        .buttonStyle(.plain)
                        SGPrimaryButton(title: "DM 시작", isLoading: isLoading, action: onConfirm)
                    }
                }
                .padding(16)
            }
            .padding(24)
        }
    }
}
