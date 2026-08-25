import SwiftUI
import Shared

/// 가입 신청중 — Compose PendingGroupsScreen 미러(레거시 JoinRequestGroupFragment).
/// GroupsView가 풀스크린 push로 표시 — 내비바는 루트 스택 몫, VM은 방문마다 새로 생성(진입 시 로드).
/// 행에 바로 신청 취소를 두고 행 클릭은 없다 — 미가입 그룹이라 이동할 상세가 없다.
struct PendingGroupsView: View {
    @StateObject private var viewModel: PendingGroupsViewModel

    var body: some View {
        PendingGroupsContent(viewModel: viewModel)
            .navigationTitle("가입 신청중")
            .navigationBarTitleDisplayMode(.inline)
            // 호출 화면이 투명 바(커버 펼침) 상태로 push해도 이 화면은 기본 내비바 — 복귀 시엔 호출 화면이 재적용
            .navigationBarScrim(visible: true)
    }

    init(container: AppContainer) {
        _viewModel = StateObject(wrappedValue: PendingGroupsViewModel(
            getMyJoinRequestedGroupsUseCase: container.getMyJoinRequestedGroupsUseCase,
            cancelJoinRequestUseCase: container.cancelJoinRequestUseCase
        ))
    }
}

private struct PendingGroupsContent: View {
    @ObservedObject var viewModel: PendingGroupsViewModel

    @Environment(\.sgColors) private var colors

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                content
            }
            .padding(16)
        }
        .background(colors.paper)
        // 당겨서 새로고침 — 시스템 스피너는 iOS 16+에서 표시(15에선 무동작, GroupsView와 동일)
        .refreshable { viewModel.onAction(.refresh) }
    }

    /// 첫 로드는 중앙 스피너, 로드 실패는 재시도 상태, 취소 실패는 목록 위 한 줄(Compose PendingGroupsContent 미러)
    @ViewBuilder private var content: some View {
        if viewModel.uiState.groups.isEmpty, viewModel.uiState.isLoading {
            ProgressView().padding(.vertical, 48)
        } else if viewModel.uiState.groups.isEmpty, let loadError = viewModel.uiState.loadError {
            VStack(spacing: 8) {
                Text(loadError).font(.subheadline).foregroundColor(colors.rust)
                Button("다시 시도") { viewModel.onAction(.refresh) }
                    .font(.subheadline)
                    .foregroundColor(colors.accent)
            }
            .padding(.vertical, 48)
        } else if viewModel.uiState.groups.isEmpty {
            SGEmptyState(title: "가입 신청중인 그룹이 없습니다", subtitle: "그룹 찾기에서 승인제 그룹에 가입을 신청해보세요.")
                .padding(.vertical, 48)
        } else {
            if let cancelError = viewModel.uiState.cancelError {
                Text(cancelError).font(.caption).foregroundColor(colors.rust)
            }
            ForEach(viewModel.uiState.groups, id: \.id) { group in
                pendingGroupRow(group)
            }
        }
    }

    /// 신청중 그룹 한 줄 — 커버/이름/멤버·가입방식 + 신청 취소(동시에 하나만 처리).
    /// 상태는 화면 제목이 말하므로 행 배지는 없다(Compose PendingGroupRow 미러)
    private func pendingGroupRow(_ group: DiscoverGroup) -> some View {
        HStack(spacing: 10) {
            ZStack {
                if let imageUrlString = group.image, let url = URL(string: imageUrlString) {
                    AsyncImage(url: url) { phase in
                        if case .success(let image) = phase {
                            image.resizable().scaledToFill()
                        } else {
                            groupCoverGradient(groupId: group.id, colors: colors)
                        }
                    }
                } else {
                    groupCoverGradient(groupId: group.id, colors: colors)
                    Text(String(group.name.prefix(1)))
                        .font(.subheadline.bold())
                        .foregroundColor(.white)
                }
            }
            .frame(width: 40, height: 40)
            .clipShape(RoundedRectangle(cornerRadius: colors.radiusButton ?? 12, style: .continuous))
            VStack(alignment: .leading, spacing: 2) {
                Text(group.name)
                    .font(.subheadline.bold())
                    .foregroundColor(colors.ink)
                    .lineLimit(1)
                // 그룹 찾기 목록 행과 동일한 요약 정보 미러(DiscoverGroupsView)
                Text("멤버 \(group.memberCount)명 · \(joinTypeLabel(group.joinType))")
                    .font(.caption)
                    .foregroundColor(colors.inkSoft)
                    .lineLimit(1)
            }
            Spacer()
            if viewModel.uiState.cancelingGroupId == group.id {
                ProgressView().padding(.horizontal, 12)
            } else {
                Button("신청 취소") { viewModel.onAction(.cancelRequest(groupId: group.id)) }
                    .font(.subheadline)
                    .foregroundColor(colors.rust)
                    .disabled(viewModel.uiState.cancelingGroupId != nil)
            }
        }
    }
}
