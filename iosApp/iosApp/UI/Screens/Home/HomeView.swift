import SwiftUI
import Shared

/// 홈(라운지) 피드 — 웹 메인 피드·Compose HomeScreen 미러(레거시 CollapsingToolbar 헤더 이식).
/// 셸이 아닌 화면이 상단바를 직접 그린다: 펼침(사진 위 투명 바+흰 콘텐츠) → 접힘(linen 스크림+잉크).
struct HomeView: View {
    @ObservedObject var viewModel: HomeViewModel

    var onNotifications: () -> Void = {}

    /// 드로어 쉘의 햄버거 메뉴(탭 쉘은 nil) — Compose homeNavigationIcon 미러
    var onMenu: (() -> Void)? = nil

    @Environment(\.sgColors) private var colors

    /// 헤더의 스크롤 좌표계 minY — 0이 펼침, 음수로 갈수록 접힘
    @State private var headerMinY: CGFloat = 0

    /// 레거시 collapsing_toolbar_layout_height(256)의 2/3 — 사용자 조정(2026-07-19)
    private let headerHeight: CGFloat = 170

    /// SGHeader 높이 미러 — 접힘 구간(스크림 페이드) 계산에 사용
    private let barHeight: CGFloat = 52

    /// 0(펼침)→1(접힘) — Compose rememberCollapseFraction 미러
    private var collapseFraction: CGFloat {
        min(max(-headerMinY / (headerHeight - barHeight), 0), 1)
    }

    var body: some View {
        GeometryReader { outer in
            ZStack(alignment: .top) {
                ScrollView {
                    VStack(spacing: 12) {
                        parallaxHeader(topInset: outer.safeAreaInsets.top)
                        feedContent
                    }
                    .padding(.bottom, 16)
                }
                collapsingBar(topInset: outer.safeAreaInsets.top)
            }
            .coordinateSpace(name: "loungeScroll")
            .onPreferenceChange(LoungeHeaderOffsetKey.self) { headerMinY = $0 }
            .background(colors.paper)
            .ignoresSafeArea(edges: .top)
        }
    }

    /// 레거시 layout_collapseMode="parallax" 미러 — 목록이 위로 갈 때 이미지는 절반 속도로 따라간다
    private func parallaxHeader(topInset: CGFloat) -> some View {
        let total = headerHeight + topInset
        return GeometryReader { geo in
            let minY = geo.frame(in: .named("loungeScroll")).minY
            ZStack(alignment: .top) {
                Image("header")
                    .resizable()
                    .scaledToFill()
                    .frame(width: geo.size.width, height: total)
                    .offset(y: minY < 0 ? -minY * 0.5 : 0)
                // 펼침 상태에서 흰 제목/아이콘 대비 확보 — Compose ParallaxHeaderImage 그라데이션 미러
                LinearGradient(
                    gradient: Gradient(colors: [Color.black.opacity(0.35), .clear]),
                    startPoint: .top,
                    endPoint: .center
                )
            }
            .frame(width: geo.size.width, height: total, alignment: .top)
            .clipped()
            .preference(key: LoungeHeaderOffsetKey.self, value: minY)
        }
        .frame(height: total)
    }

    /// 홈 전용 콜랩싱 상단바 — SGHeader의 콜랩싱 변형(레거시 contentScrim 미러).
    /// 접힘 비율에 따라 linen 배경·보더가 차오르고 콘텐츠 색이 흰색→잉크로 보간된다.
    /// (레거시 snap 플래그는 SwiftUI ScrollView에 표준 대응이 없어 Compose에서만 적용)
    private func collapsingBar(topInset: CGFloat) -> some View {
        VStack(spacing: 0) {
            HStack {
                if let onMenu {
                    Button(action: onMenu) {
                        Image(systemName: "line.3.horizontal")
                            .foregroundColor(blend(.white, colors.inkSoft, collapseFraction))
                    }
                }
                Text("우리들의 이야기")
                    .font(.headline.bold())
                    .foregroundColor(blend(.white, colors.ink, collapseFraction))
                Spacer()
                Button(action: { /* TODO: 검색 */ }) {
                    Image(systemName: "magnifyingglass")
                        .foregroundColor(blend(.white, colors.inkSoft, collapseFraction))
                }
                Button(action: onNotifications) {
                    Image(systemName: "bell.fill")
                        .foregroundColor(blend(.white, colors.inkSoft, collapseFraction))
                }
            }
            .padding(.horizontal, 16)
            .frame(height: barHeight)
            Divider().background(colors.stoneBorder.opacity(collapseFraction))
        }
        .padding(.top, topInset)
        .background(colors.linen.opacity(collapseFraction))
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

    /// 접힘 비율에 따른 콘텐츠 색 보간 — Compose lerp 미러
    private func blend(_ from: Color, _ to: Color, _ t: CGFloat) -> Color {
        let f = UIColor(from)
        let s = UIColor(to)
        var fr: CGFloat = 0, fg: CGFloat = 0, fb: CGFloat = 0, fa: CGFloat = 0
        var tr: CGFloat = 0, tg: CGFloat = 0, tb: CGFloat = 0, ta: CGFloat = 0
        f.getRed(&fr, green: &fg, blue: &fb, alpha: &fa)
        s.getRed(&tr, green: &tg, blue: &tb, alpha: &ta)
        return Color(
            red: Double(fr + (tr - fr) * t),
            green: Double(fg + (tg - fg) * t),
            blue: Double(fb + (tb - fb) * t),
            opacity: Double(fa + (ta - fa) * t)
        )
    }
}

/// 헤더 minY를 콜랩싱 바에 전달하는 PreferenceKey
private struct LoungeHeaderOffsetKey: PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) { value = nextValue() }
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
