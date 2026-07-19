import SwiftUI
import Paging
import Shared

/// 홈(라운지) 피드 — 웹 메인 피드·Compose HomeScreen 미러(레거시 CollapsingToolbar 헤더 이식).
/// 내비바(제목·툴바)는 셸이 루트 NavigationStack 위에 구성 — 최상단에선 투명(scrollEdgeAppearance)해
/// 헤더 사진이 비치고, 스크롤하면 시스템이 배경·타이틀 전환을 처리한다.
struct HomeView: View {
    @ObservedObject var viewModel: HomeViewModel

    /// Compose collectAsLazyPagingItems 미러 — 뷰 수명 동안 페이징 스트림 구독을 유지한다
    @StateObject private var lazyPagingItems: LazyPagingItems<Post>

    @Environment(\.sgColors) private var colors

    /// 첫 레이아웃 시점 헤더의 global minY — 스크롤 오프셋은 이 기준의 상대값으로 계산한다.
    /// NavigationView 안에선 rest 오프셋이 0이 아닐 수 있어(내비바 인셋), 절대값을 쓰면
    /// 헤더가 아이템과 따로 미끄러지는 어색한 움직임이 생긴다(이전 구현의 버그).
    @State private var headerRestMinY: CGFloat?

    /// 내비바 아래로 노출되는 이미지 높이 — Compose와 시각적 패리티(2026-07-19 사용자 조정).
    /// Compose는 헤더 170dp 위에 툴바 56dp가 겹쳐 바 아래 노출이 114dp인데, iOS는 전체 슬롯을
    /// topInset(상태바+내비바)+이 값으로 만들므로 노출 높이끼리 맞추려면 170이 아니라 114여야 한다.
    private let headerHeight: CGFloat = 114

    var body: some View {
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
        }
    }

    /// 레거시 layout_collapseMode="parallax" 미러 — 목록이 위로 갈 때 이미지는 절반 속도로 따라간다.
    /// 최상단에서 더 당기면 여백 대신 이미지가 늘어나며 채운다(stretchy zoom — iOS 오버스크롤 관례,
    /// Android는 시스템 오버스크롤 이펙트가 있어 Compose엔 미적용)
    private func parallaxHeader(topInset: CGFloat) -> some View {
        let total = headerHeight + topInset
        return GeometryReader { geo in
            let raw = geo.frame(in: .global).minY
            let minY = raw - (headerRestMinY ?? raw)
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
                .onAppear {
                    if headerRestMinY == nil { headerRestMinY = raw }
                }
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

    init(viewModel: HomeViewModel) {
        // Compose와 동일: 상태에서 pagingData만 뽑아낸 스트림을 collectAsLazyPagingItems로 수집
        // (Kotlin: viewModel.uiState.map { it.pagingData }.distinctUntilChanged())
        let pagingDataPublisher = viewModel.$uiState.map { $0.pagingData }.removeDuplicates { $0 === $1 }

        self.viewModel = viewModel
        _lazyPagingItems = StateObject(wrappedValue: pagingDataPublisher.collectAsLazyPagingItems())
    }
}
