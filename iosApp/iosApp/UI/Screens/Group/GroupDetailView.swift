import SwiftUI
import Paging
import Shared
// SwiftUI.Group(뷰)과 도메인 모델 Group의 동명 충돌 — 이 파일의 Group은 도메인 모델로 고정
import class Shared.Group

/// 그룹 상세 — 웹 /groups/[id]·Compose GroupDetailScreen 미러: 콜랩싱 커버(그라데이션 폴백+
/// 이름/설명/역할 칩)+멤버 스트립+피드(Paging). 상단바는 루트 NavigationStack의 기본 내비바.
/// groupId만 받아 VM이 스스로 로드한다(목록 페이징 전환으로 스냅샷 lookup 불가 — Compose 미러).
/// 계층은 Compose GroupDetailScreen과 1:1 — View=상태 소유(VM 선언), Content=구독+UI.
struct GroupDetailView: View {
    @StateObject private var viewModel: GroupDetailViewModel

    /// 글쓰기 시트(CreatePostView)의 VM 생성에 쓰인다
    private let container: AppContainer

    var body: some View {
        GroupDetailContent(viewModel: viewModel, container: container)
    }

    init(groupId: Int64, container: AppContainer) {
        _viewModel = StateObject(wrappedValue: GroupDetailViewModel(container: container, groupId: groupId))
        self.container = container
    }
}

private struct GroupDetailContent: View {
    @ObservedObject var viewModel: GroupDetailViewModel

    let container: AppContainer

    /// Compose collectAsLazyPagingItems 미러 — 뷰 수명 동안 페이징 스트림 구독을 유지한다
    @StateObject private var lazyPagingItems: LazyPagingItems<Post>

    @Environment(\.sgColors) private var colors

    /// 첫 레이아웃 시점 커버의 global minY — 스크롤 오프셋은 이 기준의 상대값(HomeView와 동일한 인셋 보정)
    @State private var headerRestMinY: CGFloat?

    /// 커버가 발행한 스크림 임계값 — 내비바 배경 수동 제어(자동 전환은 keep-alive ZStack에서 불가)
    @State private var barScrimVisible = false

    /// 내비바 아래 노출 커버 높이 — HomeView headerHeight와 동일 규칙(Compose 170dp - 툴바 56dp)
    private let headerHeight: CGFloat = 114

    /// 그룹 글쓰기 시트 — Compose CreatePostRoute(groupId) 미러
    @State private var showCreatePost = false

    var body: some View {
        GeometryReader { outer in
            ScrollView {
                VStack(spacing: 12) {
                    cover(topInset: outer.safeAreaInsets.top)
                    content
                }
                .padding(.bottom, 16)
            }
            .background(colors.paper)
            // 커버가 투명한 내비바·상태바 뒤까지 깔리도록
            .ignoresSafeArea(edges: .top)
        }
        // 레거시 fragment_group_detail.xml의 fab(bottom|end) 미러
        .overlay(alignment: .bottomTrailing) {
            SGFab(action: { showCreatePost = true }).padding(16)
        }
        // 로드 전엔 빈 제목 — 커버 그라데이션(groupId 기반)은 즉시 그려진다
        .navigationTitle(viewModel.uiState.group?.name ?? "")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showCreatePost) {
            // 성공 시 그룹 피드를 첫 페이지부터 다시 읽는다 — Compose GroupDetailScreen refreshRequested 미러
            CreatePostView(container: container, groupId: viewModel.groupId) {
                viewModel.onAction(.refreshFeed)
            }
        }
        // VM의 일회성 갱신 이벤트 — 프레젠터 refresh()가 활성 PagingSource를 무효화해
        // 같은 스트림이 새 세대(첫 페이지)를 방출한다(홈 피드와 동일 패턴)
        .onReceive(viewModel.event) { event in
            switch event {
            case .refreshFeed: lazyPagingItems.refresh()
            }
        }
        .onPreferenceChange(NavigationBarScrimVisibleKey.self) { barScrimVisible = $0 }
        .navigationBarScrim(visible: barScrimVisible)
        // 상세 진입 시 신선화 — 목록에서 받은 그룹으로 먼저 그리고 최신화한다
        .onAppear { viewModel.onAction(.refresh) }
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

    /// 그룹/멤버는 UiState, 피드는 Paging LoadState — 다음 페이지 트리거는 라이브러리가 담당.
    /// (라이브러리 LoadState.Error의 원인 에러는 internal이라 문구는 고정 메시지 사용)
    @ViewBuilder private var content: some View {
        let refreshState = lazyPagingItems.loadState.refresh
        let appendState = lazyPagingItems.loadState.append

        // 모더레이터 인박스 — 웹 GroupMemberList처럼 멤버 목록 위에 노출(joinRequests는 모더레이터에게만 채워진다)
        if !viewModel.uiState.joinRequests.isEmpty {
            joinRequestInbox.padding(.horizontal, 16)
        }
        if !viewModel.uiState.members.isEmpty {
            memberStrip.padding(.horizontal, 16)
        }
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
                        SGPostCard(post: post)
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

    /// 웹 사이드바 MemberPanel의 앱 변형 — 수평 아바타 스트립(Compose MemberStrip 미러)
    private var memberStrip: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("멤버 \(viewModel.uiState.members.count)")
                .font(.subheadline.bold())
                .foregroundColor(colors.ink)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    ForEach(viewModel.uiState.members, id: \.userId) { member in
                        VStack(spacing: 4) {
                            SGAvatar(name: member.name, imageUrl: member.profileImg)
                            Text(member.name)
                                .font(.caption2)
                                .foregroundColor(colors.inkSoft)
                                .lineLimit(1)
                        }
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    init(viewModel: GroupDetailViewModel, container: AppContainer) {
        // Compose와 동일: 상태에서 pagingData만 뽑아낸 스트림을 collectAsLazyPagingItems로 수집
        // (Kotlin: viewModel.uiState.map { it.pagingData }.distinctUntilChanged())
        let pagingDataPublisher = viewModel.$uiState.map { $0.pagingData }.removeDuplicates { $0 === $1 }

        self.viewModel = viewModel
        self.container = container
        _lazyPagingItems = StateObject(wrappedValue: pagingDataPublisher.collectAsLazyPagingItems())
    }
}
