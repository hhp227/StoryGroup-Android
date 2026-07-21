import SwiftUI
import Paging
import Shared

/// 그룹 찾기 시트 — Compose DiscoverGroupsScreen 미러(검색+정렬, 카드 탭 시 상세 다이얼로그에서 가입/신청).
/// CreatePostView와 동일하게 GroupsView가 소유한 시트로 표시(단일 진입점이라 로컬 시트가 자연스러움).
/// 계층은 Compose와 1:1 — View=상태 소유(VM 선언), Content=구독+UI.
struct DiscoverGroupsView: View {
    @StateObject private var viewModel: DiscoverGroupsViewModel

    /// 갱신용 — 그룹 탭과 같은 인스턴스(GroupsView가 소유). onAction 호출만 하므로 관찰 불필요
    private let groupsViewModel: GroupsViewModel

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationView {
            DiscoverGroupsContent(viewModel: viewModel, onJoined: { groupsViewModel.onAction(.refresh) })
                .navigationTitle("그룹 찾기")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("닫기") { dismiss() }
                    }
                }
        }
        .navigationViewStyle(.stack)
    }

    init(container: AppContainer, groupsViewModel: GroupsViewModel) {
        _viewModel = StateObject(wrappedValue: DiscoverGroupsViewModel(container: container))
        self.groupsViewModel = groupsViewModel
    }
}

private struct DiscoverGroupsContent: View {
    @ObservedObject var viewModel: DiscoverGroupsViewModel

    let onJoined: () -> Void

    /// Compose collectAsLazyPagingItems 미러 — 뷰 수명 동안 페이징 스트림 구독을 유지한다
    @StateObject private var lazyPagingItems: LazyPagingItems<DiscoverGroup>

    @Environment(\.sgColors) private var colors

    @State private var queryText = ""

    @State private var selectedGroup: DiscoverGroup? = nil

    var body: some View {
        VStack(spacing: 0) {
            VStack(spacing: 12) {
                HStack(spacing: 8) {
                    SGTextField(label: "그룹 검색", text: $queryText)
                    Button(action: { viewModel.onAction(.search(query: queryText)) }) {
                        Image(systemName: "magnifyingglass").foregroundColor(colors.accent)
                    }
                    .padding(.top, 18)
                }
                .onSubmit { viewModel.onAction(.search(query: queryText)) }
                HStack(spacing: 8) {
                    sortButton("최신순", isSelected: viewModel.uiState.sort == .recent) {
                        viewModel.onAction(.changeSort(sort: .recent))
                    }
                    sortButton("인기순", isSelected: viewModel.uiState.sort == .popular) {
                        viewModel.onAction(.changeSort(sort: .popular))
                    }
                }
                if let error = viewModel.uiState.error {
                    Text(error).font(.caption).foregroundColor(colors.rust)
                }
            }
            .padding(16)
            ScrollView {
                VStack(spacing: 12) {
                    content
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 16)
            }
        }
        .background(colors.paper)
        .onReceive(viewModel.event) { event in
            switch event {
            case .joined: onJoined()
            }
        }
        .overlay {
            if let group = selectedGroup {
                GroupDetailDialog(
                    group: group,
                    membership: viewModel.uiState.membership(of: group),
                    isLoading: viewModel.uiState.joiningGroupId == group.id,
                    onDismiss: { selectedGroup = nil },
                    onJoin: { viewModel.onAction(.join(groupId: group.id)) },
                    onCancelRequest: { viewModel.onAction(.cancelRequest(groupId: group.id)) }
                )
            }
        }
    }

    /// 로딩/에러/빈 상태는 Paging LoadState로 그린다(Compose DiscoverGroupsContent 미러)
    @ViewBuilder private var content: some View {
        let refreshState = lazyPagingItems.loadState.refresh
        let appendState = lazyPagingItems.loadState.append

        if lazyPagingItems.itemCount == 0, refreshState is LoadState.Loading {
            ProgressView().padding(.vertical, 48)
        } else if lazyPagingItems.itemCount == 0, refreshState is LoadState.Error {
            VStack(spacing: 8) {
                Text("그룹을 불러오지 못했습니다.").font(.subheadline).foregroundColor(colors.rust)
                Button("다시 시도") { lazyPagingItems.retry() }
                    .font(.subheadline)
                    .foregroundColor(colors.accent)
            }
            .padding(.vertical, 48)
        } else if lazyPagingItems.itemCount == 0 {
            SGEmptyState(title: "그룹을 찾지 못했습니다", subtitle: "다른 검색어로 시도해보세요.")
                .padding(.vertical, 48)
        } else {
            ForEach(lazyPagingItems, key: { AnyHashable($0.id) }) { group in
                if let group {
                    Button(action: { selectedGroup = group }) {
                        DiscoverGroupCard(group: group, membership: viewModel.uiState.membership(of: group))
                    }
                    .buttonStyle(.plain)
                }
            }
            SGPagingFooter(
                error: appendState is LoadState.Error ? "그룹을 더 불러오지 못했습니다." : nil,
                isLoadingMore: appendState is LoadState.Loading,
                onRetry: { lazyPagingItems.retry() }
            )
        }
    }

    private func sortButton(_ label: String, isSelected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(.subheadline.bold())
                .foregroundColor(isSelected ? colors.onAccent : colors.inkSoft)
                .frame(maxWidth: .infinity)
                .frame(height: 40)
                .background(
                    RoundedRectangle(cornerRadius: colors.radiusButton ?? 20, style: .continuous)
                        .fill(isSelected ? colors.accent : Color.clear)
                        .overlay(
                            RoundedRectangle(cornerRadius: colors.radiusButton ?? 20, style: .continuous)
                                .stroke(isSelected ? Color.clear : colors.stoneBorder, lineWidth: 1)
                        )
                )
        }
        .buttonStyle(.plain)
    }

    init(viewModel: DiscoverGroupsViewModel, onJoined: @escaping () -> Void) {
        let pagingDataPublisher = viewModel.$uiState.map { $0.pagingData }.removeDuplicates { $0 === $1 }

        self.viewModel = viewModel
        self.onJoined = onJoined
        _lazyPagingItems = StateObject(wrappedValue: pagingDataPublisher.collectAsLazyPagingItems())
    }
}

private struct DiscoverGroupCard: View {
    let group: DiscoverGroup

    let membership: GroupMembershipStatus

    @Environment(\.sgColors) private var colors

    var body: some View {
        SGCard {
            HStack(spacing: 12) {
                if let imageUrlString = group.image, let url = URL(string: imageUrlString) {
                    AsyncImage(url: url) { phase in
                        if case .success(let image) = phase {
                            image.resizable().scaledToFill()
                        } else {
                            groupCoverGradient(groupId: group.id, colors: colors)
                        }
                    }
                    .frame(width: 48, height: 48)
                    .clipShape(RoundedRectangle(cornerRadius: colors.radiusButton ?? 12, style: .continuous))
                } else {
                    ZStack {
                        RoundedRectangle(cornerRadius: colors.radiusButton ?? 12, style: .continuous)
                            .fill(groupCoverGradient(groupId: group.id, colors: colors))
                            .frame(width: 48, height: 48)
                        Text(String(group.name.prefix(1)))
                            .font(.headline.bold())
                            .foregroundColor(.white)
                    }
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text(group.name).font(.headline).foregroundColor(colors.ink).lineLimit(1)
                    Text("멤버 \(group.memberCount)명 · \(joinTypeLabel(group.joinType))")
                        .font(.caption)
                        .foregroundColor(colors.inkSoft)
                }
                Spacer()
                membershipBadge
            }
            .padding(16)
        }
    }

    @ViewBuilder private var membershipBadge: some View {
        switch membership {
        case .member: badge("가입됨")
        case .pending: badge("신청됨")
        default: EmptyView()
        }
    }

    private func badge(_ label: String) -> some View {
        Text(label)
            .font(.caption2.weight(.medium))
            .foregroundColor(colors.accent2)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(colors.accent2Soft)
            .cornerRadius(colors.radiusButton ?? 12)
    }
}

/// 웹 GroupDetailDialog 미러 — 커버/이름/전체 설명/멤버 수/가입 방식+상태별 가입 액션.
/// 시스템 다이얼로그 대신 반투명 배경+중앙 카드로 직접 구현(iOS 15 공통, presentationDetents 불필요)
private struct GroupDetailDialog: View {
    let group: DiscoverGroup

    let membership: GroupMembershipStatus

    let isLoading: Bool

    let onDismiss: () -> Void

    let onJoin: () -> Void

    let onCancelRequest: () -> Void

    @Environment(\.sgColors) private var colors

    var body: some View {
        ZStack {
            Color.black.opacity(0.35)
                .ignoresSafeArea()
                .onTapGesture(perform: onDismiss)
            SGCard {
                VStack(alignment: .leading, spacing: 12) {
                    HStack(spacing: 12) {
                        if let imageUrlString = group.image, let url = URL(string: imageUrlString) {
                            AsyncImage(url: url) { phase in
                                if case .success(let image) = phase {
                                    image.resizable().scaledToFill()
                                } else {
                                    groupCoverGradient(groupId: group.id, colors: colors)
                                }
                            }
                            .frame(width: 56, height: 56)
                            .clipShape(RoundedRectangle(cornerRadius: colors.radiusButton ?? 12, style: .continuous))
                        } else {
                            ZStack {
                                RoundedRectangle(cornerRadius: colors.radiusButton ?? 12, style: .continuous)
                                    .fill(groupCoverGradient(groupId: group.id, colors: colors))
                                    .frame(width: 56, height: 56)
                                Text(String(group.name.prefix(1)))
                                    .font(.title2.bold())
                                    .foregroundColor(.white)
                            }
                        }
                        VStack(alignment: .leading, spacing: 2) {
                            Text(group.name).font(.headline).foregroundColor(colors.ink)
                            Text("멤버 \(group.memberCount)명 · \(joinTypeLabel(group.joinType))")
                                .font(.caption)
                                .foregroundColor(colors.inkSoft)
                        }
                        Spacer()
                        Button(action: onDismiss) {
                            Image(systemName: "xmark").foregroundColor(colors.inkSoft)
                        }
                    }
                    if let description = group.description_, !description.isEmpty {
                        Text(description).font(.subheadline).foregroundColor(colors.ink)
                    }
                    switch membership {
                    case .member:
                        SGPrimaryButton(title: "가입됨", enabled: false, action: {})
                    case .pending:
                        SGPrimaryButton(title: "신청 취소", isLoading: isLoading, action: onCancelRequest)
                    default:
                        SGPrimaryButton(
                            title: group.joinType == .autoApprove ? "가입" : "신청",
                            isLoading: isLoading,
                            action: onJoin
                        )
                    }
                }
                .padding(16)
            }
            .padding(24)
        }
    }
}

/// 웹 joinTypeLabel 미러 — Compose joinTypeLabel과 동일
func joinTypeLabel(_ joinType: GroupJoinType) -> String {
    switch joinType {
    case .autoApprove: return "자동 승인"
    default: return "승인제"
    }
}
