import SwiftUI
import Shared

/// 그룹 상세 — 웹 /groups/[id]·Compose GroupDetailScreen 미러: 콜랩싱 커버(그라데이션 폴백+
/// 이름/설명/역할 칩)+멤버 스트립+피드. 상단바는 탭 NavigationView의 기본 내비바(백 버튼 자동,
/// 최상단 투명→스크롤 시 시스템이 배경·타이틀 전환).
struct GroupDetailView: View {
    @StateObject private var viewModel: GroupDetailViewModel

    @Environment(\.sgColors) private var colors

    /// 첫 레이아웃 시점 커버의 global minY — 스크롤 오프셋은 이 기준의 상대값(HomeView와 동일한 인셋 보정)
    @State private var headerRestMinY: CGFloat?

    /// 내비바 아래 노출 커버 높이 — HomeView headerHeight와 동일 규칙(Compose 170dp - 툴바 56dp)
    private let headerHeight: CGFloat = 114

    init(group: Group, factory: (Group) -> GroupDetailViewModel) {
        _viewModel = StateObject(wrappedValue: factory(group))
    }

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
        .navigationTitle(viewModel.uiState.group.name)
        .navigationBarTitleDisplayMode(.inline)
        // 상세 진입 시 신선화 — 목록에서 받은 그룹으로 먼저 그리고 최신화한다
        .onAppear { viewModel.onAction(.refresh) }
    }

    /// 커버 배너 — 이미지 로딩(④) 전까지 웹 GroupCover 그라데이션 폴백. 패럴럭스+stretchy는 HomeView 미러
    private func cover(topInset: CGFloat) -> some View {
        let total = headerHeight + topInset
        return GeometryReader { geo in
            let raw = geo.frame(in: .global).minY
            let minY = raw - (headerRestMinY ?? raw)
            let stretch = max(0, minY)
            ZStack(alignment: .bottomLeading) {
                groupCoverGradient(groupId: viewModel.uiState.group.id, colors: colors)
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
        }
        .frame(height: total)
    }

    /// 웹 커버 스크림 위 그룹명/설명/역할 칩 미러
    private var coverOverlay: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack(spacing: 8) {
                Text(viewModel.uiState.group.name)
                    .font(.title3.bold())
                    .foregroundColor(.white)
                    .lineLimit(1)
                RoleChip(role: viewModel.uiState.group.myRole)
            }
            if let description = viewModel.uiState.group.description_, !description.isEmpty {
                Text(description)
                    .font(.caption)
                    .foregroundColor(.white.opacity(0.88))
                    .lineLimit(2)
            }
        }
    }

    @ViewBuilder private var content: some View {
        if viewModel.uiState.posts.isEmpty && viewModel.uiState.members.isEmpty && viewModel.uiState.isLoading {
            ProgressView().padding(.vertical, 48)
        } else if viewModel.uiState.posts.isEmpty && viewModel.uiState.members.isEmpty,
                  let error = viewModel.uiState.error {
            VStack(spacing: 8) {
                Text(error).font(.subheadline).foregroundColor(colors.rust)
                Button("다시 시도") { viewModel.onAction(.refresh) }
                    .font(.subheadline)
                    .foregroundColor(colors.accent)
            }
            .padding(.vertical, 48)
        } else {
            if !viewModel.uiState.members.isEmpty {
                memberStrip.padding(.horizontal, 16)
            }
            if viewModel.uiState.posts.isEmpty && !viewModel.uiState.isLoading {
                SGEmptyState(title: "아직 이야기가 없습니다", subtitle: "첫 이야기를 남겨보세요.")
                    .padding(.vertical, 48)
            } else {
                LazyVStack(spacing: 12) {
                    ForEach(viewModel.uiState.posts, id: \.id) { post in
                        SGPostCard(post: post)
                            .onAppear {
                                // 웹 sentinel 미러 — 마지막 카드가 보이면 다음 페이지를 읽는다
                                if post.id == viewModel.uiState.posts.last?.id { viewModel.onAction(.loadMore) }
                            }
                    }
                    SGPagingFooter(
                        error: viewModel.uiState.error,
                        isLoadingMore: viewModel.uiState.isLoadingMore,
                        onRetry: { viewModel.onAction(.loadMore) }
                    )
                }
                .padding(.horizontal, 16)
            }
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
                            SGAvatar(name: member.name)
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
}
