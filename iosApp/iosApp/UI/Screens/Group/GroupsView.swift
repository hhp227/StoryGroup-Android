import SwiftUI
import Paging
import Shared
// SwiftUI.Group(뷰)과 도메인 모델 Group의 동명 충돌 — 이 파일의 Group은 도메인 모델로 고정
import class Shared.Group

/// 가입중인 그룹 목록 + 만들기/찾기 진입 — 웹 /groups·Compose GroupsScreen 미러(라운지 제외, 페이징).
/// 상세는 루트 NavigationStack 풀스크린 push(onOpenGroup) — Compose NavHost(GroupDetailRoute) 미러.
/// 계층은 Compose GroupsScreen과 1:1 — View=상태 소유(VM 선언), Content=구독+UI.
struct GroupsView: View {
    @StateObject private var viewModel: GroupsViewModel

    let onOpenGroup: (Group) -> Void

    /// 화면이 자기 시트를 만들 때 쓴다 — 그룹 만들기/찾기 시트가 이 인스턴스를 그대로 전달받아 갱신한다
    private let container: AppContainer

    var body: some View {
        GroupsContent(viewModel: viewModel, container: container, onOpenGroup: onOpenGroup)
    }

    init(container: AppContainer, onOpenGroup: @escaping (Group) -> Void) {
        _viewModel = StateObject(wrappedValue: GroupsViewModel(getMyGroupsPagingDataUseCase: container.getMyGroupsPagingDataUseCase))
        self.container = container
        self.onOpenGroup = onOpenGroup
    }
}

private struct GroupsContent: View {
    @ObservedObject var viewModel: GroupsViewModel

    let container: AppContainer

    let onOpenGroup: (Group) -> Void

    /// Compose collectAsLazyPagingItems 미러 — 뷰 수명 동안 페이징 스트림 구독을 유지한다
    @StateObject private var lazyPagingItems: LazyPagingItems<Group>

    @Environment(\.sgColors) private var colors

    /// 그룹 만들기/찾기 — CreatePostView와 동일하게 GroupsView가 소유한 시트(단일 진입점)
    @State private var showCreateGroup = false

    @State private var showDiscoverGroups = false

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                HStack(spacing: 8) {
                    groupActionButton("그룹 만들기") { showCreateGroup = true }
                    groupActionButton("그룹 찾기") { showDiscoverGroups = true }
                }
                content
            }
            .padding(16)
        }
        .background(colors.paper)
        // VM의 일회성 갱신 이벤트 — 프레젠터 refresh()가 활성 PagingSource를 무효화해
        // 같은 스트림이 새 세대(첫 페이지)를 방출한다(홈 피드와 동일 패턴)
        .onReceive(viewModel.event) { event in
            switch event {
            case .refresh: lazyPagingItems.refresh()
            }
        }
        .sheet(isPresented: $showCreateGroup) {
            CreateGroupView(container: container, groupsViewModel: viewModel)
        }
        .sheet(isPresented: $showDiscoverGroups) {
            DiscoverGroupsView(container: container, groupsViewModel: viewModel)
        }
    }

    /// 로딩/에러/빈 상태는 Paging LoadState로 그린다(Compose GroupsContent 미러).
    /// (라이브러리 LoadState.Error의 원인 에러는 internal이라 문구는 고정 메시지 사용)
    @ViewBuilder private var content: some View {
        let refreshState = lazyPagingItems.loadState.refresh
        let appendState = lazyPagingItems.loadState.append

        if lazyPagingItems.itemCount == 0, refreshState is LoadState.Loading {
            ProgressView().padding(.vertical, 48)
        } else if lazyPagingItems.itemCount == 0, refreshState is LoadState.Error {
            VStack(spacing: 8) {
                Text("그룹 목록을 불러오지 못했습니다.").font(.subheadline).foregroundColor(colors.rust)
                Button("다시 시도") { lazyPagingItems.retry() }
                    .font(.subheadline)
                    .foregroundColor(colors.accent)
            }
            .padding(.vertical, 48)
        } else if lazyPagingItems.itemCount == 0 {
            SGEmptyState(title: "아직 그룹이 없습니다", subtitle: "새 그룹을 만들거나 그룹 찾기에서 참여해보세요.")
                .padding(.vertical, 48)
        } else {
            // 웹 /groups 내 그룹 탭(auto-fill minmax(160px,1fr) CSS 그리드) 미러 — 그룹 찾기(목록)와 달리
            // 내 그룹은 그리드로 보여준다(Compose LazyVerticalGrid 미러)
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 152), spacing: 12)], spacing: 16) {
                ForEach(lazyPagingItems, key: { AnyHashable($0.id) }) { group in
                    if let group {
                        Button(action: { onOpenGroup(group) }) {
                            GroupCard(group: group)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            SGPagingFooter(
                error: appendState is LoadState.Error ? "그룹 목록을 더 불러오지 못했습니다." : nil,
                isLoadingMore: appendState is LoadState.Loading,
                onRetry: { lazyPagingItems.retry() }
            )
        }
    }

    private func groupActionButton(_ title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.subheadline.bold())
                .foregroundColor(colors.accent)
                .frame(maxWidth: .infinity)
                .frame(height: 44)
                .background(
                    RoundedRectangle(cornerRadius: colors.radiusButton ?? 22, style: .continuous)
                        .stroke(colors.stoneBorder, lineWidth: 1)
                )
        }
    }

    init(viewModel: GroupsViewModel, container: AppContainer, onOpenGroup: @escaping (Group) -> Void) {
        // Compose와 동일: 상태에서 pagingData만 뽑아낸 스트림을 collectAsLazyPagingItems로 수집
        let pagingDataPublisher = viewModel.$uiState.map { $0.pagingData }.removeDuplicates { $0 === $1 }

        self.viewModel = viewModel
        self.container = container
        self.onOpenGroup = onOpenGroup
        _lazyPagingItems = StateObject(wrappedValue: pagingDataPublisher.collectAsLazyPagingItems())
    }
}

/// 웹 GroupCard 미러 — 정사각 커버(역할 칩 오버레이) + 이름/소개, 카드 배경 없이 그리드 타일로
private struct GroupCard: View {
    let group: Group

    @Environment(\.sgColors) private var colors

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            // scaledToFill 이미지는 제안 프레임보다 커질 수 있어 GeometryReader로 셀 크기를 고정한다
            GeometryReader { geometry in
                ZStack(alignment: .topTrailing) {
                    cover
                        .frame(width: geometry.size.width, height: geometry.size.height)
                    if group.myRole != .member {
                        RoleChip(role: group.myRole).padding(8)
                    }
                }
                .clipShape(RoundedRectangle(cornerRadius: colors.radiusCard, style: .continuous))
            }
            .aspectRatio(1, contentMode: .fit)

            VStack(alignment: .leading, spacing: 2) {
                Text(group.name)
                    .font(.subheadline.bold())
                    .foregroundColor(colors.ink)
                    .lineLimit(1)
                if let description = group.description_, !description.isEmpty {
                    Text(description)
                        .font(.caption)
                        .foregroundColor(colors.inkSoft)
                        .lineLimit(2)
                }
            }
        }
    }

    // 웹 GroupCover 미러 — group.image 있으면 실사진, 없으면 그룹별 그라데이션+이니셜 폴백.
    // (SwiftUI.Group 래퍼는 쓰지 않는다 — 이 파일은 도메인 Group을 스코프 임포트해서 이름이 겹친다)
    @ViewBuilder private var cover: some View {
        if let imageUrlString = group.image, let url = URL(string: imageUrlString) {
            AsyncImage(url: url) { phase in
                if case .success(let image) = phase {
                    image.resizable().scaledToFill()
                } else {
                    groupCoverGradient(groupId: group.id, colors: colors)
                }
            }
        } else {
            ZStack {
                groupCoverGradient(groupId: group.id, colors: colors)
                Text(String(group.name.prefix(1)))
                    .font(.largeTitle.bold())
                    .foregroundColor(.white)
            }
        }
    }
}

/// 웹 roleLabel 미러 — Compose roleLabel과 동일
func roleLabel(_ role: GroupRole) -> String {
    switch role {
    case .owner: return "방장"
    case .admin: return "부방장"
    default: return "멤버"
    }
}

/// 역할 칩 — 웹 roleChipClass 미러(방장=accent, 부방장 등=accent2)
struct RoleChip: View {
    let role: GroupRole

    @Environment(\.sgColors) private var colors

    var body: some View {
        Text(roleLabel(role))
            .font(.caption2.weight(.medium))
            .foregroundColor(role == .owner ? colors.accent : colors.accent2)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(role == .owner ? colors.accentSoft : colors.accent2Soft)
            .cornerRadius(colors.radiusButton ?? 12)
    }
}

/// 웹 GroupCover 폴백 미러 — COVER_COLORS[id%4] → accent2 그라데이션(135deg)
func groupCoverGradient(groupId: Int64, colors: SGColors) -> LinearGradient {
    let bases = [colors.accent, colors.accent2, colors.moss, colors.amber]
    let base = bases[Int(groupId % 4)]
    return LinearGradient(
        gradient: Gradient(colors: [base, colors.accent2]),
        startPoint: .topLeading,
        endPoint: .bottomTrailing
    )
}
