import SwiftUI
import Paging
import Shared

/// 홈(라운지) 피드 — 웹 메인 피드·Compose HomeScreen 미러(레거시 CollapsingToolbar 헤더 이식).
/// ViewModel은 화면이 소유한다(Compose HomeScreen의 default parameter 선언 미러) — keep-alive
/// ZStack 안이라 탭 전환에도 살아있고, 로그아웃 시 MainShellView와 함께 소멸한다.
/// VM lazy 생성(StateObject)과 페이징 구독 분리는 GroupDetailView와 동일한 2계층 구조.
struct HomeView: View {
    @StateObject private var homeViewModel = HomeViewModel()

    /// 게시글 상세→작성자 프로필 체인이 쓴다(Task 9 PostDetailView 호출부) — 이 태스크에서는 전달만
    private let chatViewModel: ChatViewModel

    private let profileViewModel: ProfileViewModel

    var body: some View {
        HomeContent(viewModel: homeViewModel, chatViewModel: chatViewModel, profileViewModel: profileViewModel)
    }

    init(chatViewModel: ChatViewModel, profileViewModel: ProfileViewModel) {
        self.chatViewModel = chatViewModel
        self.profileViewModel = profileViewModel
    }
}

/// 내비바(제목·툴바)는 셸이 루트 NavigationStack 위에 구성 — 최상단에선 투명(scrollEdgeAppearance)해
/// 헤더 사진이 비치고, 스크롤하면 시스템이 배경·타이틀 전환을 처리한다.
private struct HomeContent: View {
    @ObservedObject var viewModel: HomeViewModel

    /// 게시글 상세→작성자 프로필 체인이 쓴다(Task 9 PostDetailView 호출부) — 이 태스크에서는 전달만
    let chatViewModel: ChatViewModel

    let profileViewModel: ProfileViewModel

    /// Compose collectAsLazyPagingItems 미러 — 뷰 수명 동안 페이징 스트림 구독을 유지한다
    @StateObject private var lazyPagingItems: LazyPagingItems<Post>

    @Environment(\.sgColors) private var colors

    /// 라운지 글쓰기 풀스크린 push — Compose CreatePostRoute(groupId=null) 미러(그룹 상세와 동일하게 화면 소유)
    @State private var showCreatePost = false

    /// 공유 시트 대상 — 카드 공유 버튼이 채우면 ActivityShareSheet가 뜬다(Compose postShareText 미러)
    @State private var shareItem: ShareItem?

    /// 내비바 아래로 노출되는 이미지 높이 — Compose와 시각적 패리티(2026-07-19 사용자 조정).
    /// Compose는 헤더 170dp 위에 툴바 56dp가 겹쳐 바 아래 노출이 114dp인데, iOS는 전체 슬롯을
    /// topInset(상태바+내비바)+이 값으로 만들므로 노출 높이끼리 맞추려면 170이 아니라 114여야 한다.
    /// 스크롤 변위 측정용 좌표계 이름 — ScrollView에 건다
    private static let scrollSpace = "homeScroll"

    private let headerHeight: CGFloat = 114

    /// 글쓰기를 화면 안에서 push — NavigationStack은 iOS 16+라 iOS 15는 숨김 NavigationLink 폴백(그룹 상세 미러)
    var body: some View {
        if #available(iOS 16.0, *) {
            core.navigationDestination(isPresented: $showCreatePost) { createPostDestination }
        } else {
            core.background(
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
                    parallaxHeader(topInset: outer.safeAreaInsets.top)
                    feedContent
                }
                .padding(.bottom, 16)
            }
            .background(colors.paper)
            // 헤더 사진이 투명한 내비바·상태바 뒤까지 깔리도록
            .ignoresSafeArea(edges: .top)
            // 스크롤 변위 측정 기준 — 전역 좌표는 로딩 중 값이 튀어 헤더가 떨린다(그룹 상세 미러)
            .coordinateSpace(name: Self.scrollSpace)
            // 당겨서 새로고침 — 글쓰기 복귀와 같은 Refresh 경로(VM Event → lazyPagingItems.refresh())를 탄다.
            // Compose HomeScreen 미러 — ScrollView의 시스템 스피너는 iOS 16+에서 표시(15에선 무동작)
            .refreshable {
                viewModel.onAction(.refresh)
                await lazyPagingItems.awaitRefresh()
            }
        }
        // 레거시 fragment_lounge.xml의 fab(bottom|end) 미러
        .overlay(alignment: .bottomTrailing) {
            SGFab(action: { showCreatePost = true }).padding(16)
        }
        // VM의 일회성 갱신 이벤트 — 프레젠터 refresh()가 활성 PagingSource를 무효화해
        // 같은 스트림이 새 세대(첫 페이지, 라운지 재해석 포함)를 방출한다
        .onReceive(viewModel.event) { event in
            switch event {
            case .refresh: lazyPagingItems.refresh()
            }
        }
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

    private var createPostDestination: some View {
        // 성공 시 라운지 피드를 첫 페이지부터 다시 읽는다 — Compose HomeScreen refreshRequested 미러
        CreatePostView(groupId: nil) {
            viewModel.onAction(.refresh)
        }
    }

    /// 레거시 layout_collapseMode="parallax" 미러 — 목록이 위로 갈 때 이미지는 절반 속도로 따라간다.
    /// 최상단에서 더 당기면 여백 대신 이미지가 늘어나며 채운다(stretchy zoom — iOS 오버스크롤 관례,
    /// Android는 시스템 오버스크롤 이펙트가 있어 Compose엔 미적용)
    private func parallaxHeader(topInset: CGFloat) -> some View {
        let total = headerHeight + topInset
        return GeometryReader { geo in
            // 스크롤 변위 — rest에서 정확히 0. `+ topInset`의 이유와, 전역 좌표·기준값 보정으로
            // 되돌리면 안 되는 이유는 GroupDetailView의 같은 자리 주석 참고(셋 다 겪었다)
            let raw = geo.frame(in: .named(Self.scrollSpace)).minY + topInset
            let minY = raw
            let stretch = max(0, minY)
            Image("header")
                .resizable()
                .scaledToFill()
                // 패럴럭스는 클리핑 안쪽의 렌더 이동 — 밀려난 부분이 피드 위로 새지 않는다
                .offset(y: minY < 0 ? -minY * 0.5 : 0)
                .frame(width: geo.size.width, height: total + stretch, alignment: .top)
                .clipped()
                // 클리핑 뒤에 당긴 만큼 끌어올려 이미지 상단을 화면 상단에 고정 —
                // clipped보다 먼저 옮기면 늘어난 윗부분이 잘려나간다(이전 구현의 버그)
                .offset(y: -stretch)
                // 피드 아이템이 내비바 영역에 닿는 시점부터 바 배경을 켠다.
                // rest 보정값(minY)이 아니라 화면 기하(raw: 헤더 하단 raw+total ≤ 바 하단 topInset,
                // 정리하면 raw ≤ -headerHeight)로 판정 — 셸(탭/드로어)별 첫 레이아웃 오프셋 차이로
                // 전환 시점이 어긋나던 문제 방지
                .preference(key: NavigationBarScrimVisibleKey.self, value: raw <= -headerHeight)
        }
        .frame(height: total)
    }

    /// 피드 본문 — 로딩/에러/빈 상태는 Paging LoadState로 그린다(Compose HomeScreen 미러).
    /// 다음 페이지 트리거는 라이브러리(prefetchDistance)가 담당.
    /// (라이브러리 LoadState.Error의 원인 에러는 internal이라 문구는 고정 메시지 사용)
    @ViewBuilder private var feedContent: some View {
        let refreshState = lazyPagingItems.loadState.refresh
        let appendState = lazyPagingItems.loadState.append

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
                        // 이 VM이 스냅샷에서 그 글을 걷어낸다(전체 재조회 없음)
                        NavigationLink {
                            PostDetailView(
                                groupId: post.groupId,
                                postId: post.id,
                                chatViewModel: chatViewModel,
                                profileViewModel: profileViewModel
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

    init(viewModel: HomeViewModel, chatViewModel: ChatViewModel, profileViewModel: ProfileViewModel) {
        // Compose와 동일: 상태에서 pagingData만 뽑아낸 스트림을 collectAsLazyPagingItems로 수집
        // (Kotlin: viewModel.uiState.map { it.pagingData }.distinctUntilChanged())
        let pagingDataPublisher = viewModel.$uiState.map { $0.pagingData }.removeDuplicates { $0 === $1 }

        self.viewModel = viewModel
        self.chatViewModel = chatViewModel
        self.profileViewModel = profileViewModel
        _lazyPagingItems = StateObject(wrappedValue: pagingDataPublisher.collectAsLazyPagingItems())
    }
}
