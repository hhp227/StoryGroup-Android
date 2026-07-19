import SwiftUI
import Shared

/// 홈(라운지) 피드 — 웹 메인 피드·Compose HomeScreen 미러(레거시 CollapsingToolbar 헤더 이식).
/// 상단바는 기본 NavigationBar(사용자 지시): 최상단에선 투명(scrollEdgeAppearance)해 헤더 사진이
/// 비치고, 스크롤하면 시스템이 배경·타이틀 전환을 처리한다.
struct HomeView: View {
    @ObservedObject var viewModel: HomeViewModel

    var onNotifications: () -> Void = {}

    /// 드로어 쉘의 햄버거 메뉴(탭 쉘은 nil) — Compose homeNavigationIcon 미러
    var onMenu: (() -> Void)? = nil

    @Environment(\.sgColors) private var colors

    /// 첫 레이아웃 시점 헤더의 global minY — 스크롤 오프셋은 이 기준의 상대값으로 계산한다.
    /// NavigationView 안에선 rest 오프셋이 0이 아닐 수 있어(내비바 인셋), 절대값을 쓰면
    /// 헤더가 아이템과 따로 미끄러지는 어색한 움직임이 생긴다(이전 구현의 버그).
    @State private var headerRestMinY: CGFloat?

    /// 레거시 collapsing_toolbar_layout_height(256)의 2/3 — 사용자 조정(2026-07-19)
    private let headerHeight: CGFloat = 170

    var body: some View {
        NavigationView {
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
            .navigationTitle("우리들의 이야기")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    if let onMenu {
                        Button(action: onMenu) { Image(systemName: "line.3.horizontal") }
                    }
                }
                ToolbarItemGroup(placement: .navigationBarTrailing) {
                    Button(action: { /* TODO: 검색 */ }) { Image(systemName: "magnifyingglass") }
                    Button(action: onNotifications) { Image(systemName: "bell.fill") }
                }
            }
        }
        // 탭 콘텐츠 영역 안의 단일 컬럼 — iPad에서 사이드바로 갈라지지 않게
        .navigationViewStyle(.stack)
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

    /// 피드 본문 — 상태(로딩/에러/빈)도 헤더 아래 목록 영역에 그린다(Compose HomeScreen 미러)
    @ViewBuilder private var feedContent: some View {
        if viewModel.uiState.posts.isEmpty && viewModel.uiState.isLoading {
            ProgressView().padding(.vertical, 48)
        } else if viewModel.uiState.posts.isEmpty, let error = viewModel.uiState.error {
            VStack(spacing: 8) {
                Text(error).font(.subheadline).foregroundColor(colors.rust)
                Button("다시 시도") { viewModel.onAction(.refresh) }
                    .font(.subheadline)
                    .foregroundColor(colors.accent)
            }
            .padding(.vertical, 48)
        } else if viewModel.uiState.posts.isEmpty {
            SGEmptyState(title: "아직 이야기가 없습니다", subtitle: "첫 이야기를 남겨보세요.")
                .padding(.vertical, 48)
        } else {
            LazyVStack(spacing: 12) {
                ForEach(viewModel.uiState.posts, id: \.id) { post in
                    FeedPostCard(post: post)
                        .onAppear {
                            // 웹 sentinel 미러 — 마지막 카드가 보이면 다음 페이지를 읽는다
                            if post.id == viewModel.uiState.posts.last?.id { viewModel.onAction(.loadMore) }
                        }
                }
                feedFooter
            }
            .padding(.horizontal, 16)
        }
    }

    /// 추가 로딩/실패 표시 — 실패 시엔 수동 재시도만 노출(자동 재시도 루프 방지)
    @ViewBuilder private var feedFooter: some View {
        if let error = viewModel.uiState.error {
            VStack(spacing: 4) {
                Text(error).font(.caption).foregroundColor(colors.rust)
                Button("다시 시도") { viewModel.onAction(.loadMore) }
                    .font(.caption)
                    .foregroundColor(colors.accent)
            }
            .padding(.vertical, 8)
        } else if viewModel.uiState.isLoadingMore {
            ProgressView().padding(8)
        }
    }
}

private struct FeedPostCard: View {
    let post: Post

    @Environment(\.sgColors) private var colors

    var body: some View {
        SGCard {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 10) {
                    SGAvatar(name: post.authorName)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(post.authorName).font(.subheadline.bold()).foregroundColor(colors.ink)
                        Text(TimeFormats.relative(post.createdAt)).font(.caption).foregroundColor(colors.inkFaint)
                    }
                    Spacer()
                    if post.isNotice {
                        Text("공지")
                            .font(.caption2.weight(.medium))
                            .foregroundColor(colors.accent)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(colors.accentSoft)
                            .cornerRadius(colors.radiusButton ?? 12)
                    }
                }
                if !post.text.isEmpty {
                    Text(post.text)
                        .font(.subheadline)
                        .foregroundColor(colors.ink)
                        .lineLimit(6)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                // TODO: 첨부 이미지는 이미지 로딩 도입(다음 단계 ④) 후 실제 렌더링으로 교체
                if !attachmentSummary.isEmpty {
                    Text(attachmentSummary).font(.caption).foregroundColor(colors.inkSoft)
                }
            }
            .padding(16)
        }
    }

    private var attachmentSummary: String {
        var parts: [String] = []
        if !post.imageUrls.isEmpty { parts.append("사진 \(post.imageUrls.count)장") }
        if !post.videoUrls.isEmpty { parts.append("동영상 \(post.videoUrls.count)개") }
        return parts.joined(separator: " · ")
    }
}
