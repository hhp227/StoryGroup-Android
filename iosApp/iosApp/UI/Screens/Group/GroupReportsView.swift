import SwiftUI
import Shared

/// 그룹 신고함(모더레이터 전용) — composeApp GroupReportsScreen.kt와 1:1 미러
/// (웹 /groups/[id]/reports). 진입점은 그룹 설정 탭 "신고함" 메뉴(GroupDetailView push).
/// 신고된 게시글 탭 → 게시글 상세 자체 push(조치는 상세의 기존 기능으로 한다).
struct GroupReportsView: View {
    let groupId: Int64

    // 게시글 상세 push 체인에 필요 — 셸 소유 세션 VM pass-through(GroupDetailView 선례)
    private let chatViewModel: ChatViewModel

    private let profileViewModel: ProfileViewModel

    @StateObject private var groupReportsViewModel: GroupReportsViewModel

    /// 신고된 게시글 탭 → 상세 push(메뉴가 아니라 행 탭이지만 상태 push 관용구 유지)
    @State private var selectedPostId: Int64? = nil

    @Environment(\.sgColors) private var colors

    /// 자체 push 1종 — iOS 16 navigationDestination / iOS 15 숨김 NavigationLink 폴백(GroupDetailView 선례)
    var body: some View {
        if #available(iOS 16.0, *) {
            content
                .navigationTitle("신고함")
                .navigationBarTitleDisplayMode(.inline)
                // 호출 화면이 투명 바(커버 펼침) 상태로 push해도 이 화면은 기본 내비바 — 복귀 시엔 호출 화면이 재적용
                .navigationBarScrim(visible: true)
                .navigationDestination(isPresented: showPostDetail) { postDetailDestination }
        } else {
            content
                .navigationTitle("신고함")
                .navigationBarTitleDisplayMode(.inline)
                // 호출 화면이 투명 바(커버 펼침) 상태로 push해도 이 화면은 기본 내비바 — 복귀 시엔 호출 화면이 재적용
                .navigationBarScrim(visible: true)
                .background(
                    NavigationLink(isActive: showPostDetail) { postDetailDestination } label: { EmptyView() }.hidden()
                )
        }
    }

    @ViewBuilder private var content: some View {
        let uiState = groupReportsViewModel.uiState

        VStack(alignment: .leading, spacing: 8) {
            Text("멤버가 신고한 게시글입니다. 확인/기각은 처리 기록이며, 게시글 삭제 등 조치는 게시글에서 직접 합니다.")
                .font(.footnote)
                .foregroundColor(colors.inkFaint)
                .padding(.horizontal, 16)
                .padding(.top, 8)
            HStack(spacing: 8) {
                filterChip("대기중", active: uiState.filter == .pending) {
                    groupReportsViewModel.onAction(.setFilter(.pending))
                }
                filterChip("전체", active: uiState.filter == nil) {
                    groupReportsViewModel.onAction(.setFilter(nil))
                }
            }
            .padding(.horizontal, 16)
            listBody(uiState)
        }
        .background(colors.paper.ignoresSafeArea())
    }

    @ViewBuilder private func listBody(_ uiState: GroupReportsViewModel.UiState) -> some View {
        if uiState.reports == nil && uiState.isLoading {
            ProgressView()
                .tint(colors.accent)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let reports = uiState.reports {
            if reports.isEmpty {
                VStack(spacing: 8) {
                    Text(uiState.filter == .pending ? "대기중인 신고가 없습니다." : "신고 내역이 없습니다.")
                        .font(.subheadline.bold())
                        .foregroundColor(colors.ink)
                    Text("멤버가 게시글을 신고하면 여기로 접수됩니다.")
                        .font(.footnote)
                        .foregroundColor(colors.inkFaint)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    VStack(spacing: 8) {
                        if let actionError = uiState.actionError {
                            Text(actionError)
                                .font(.footnote)
                                .foregroundColor(colors.rust)
                        }
                        ForEach(reports, id: \.id) { report in
                            reportCard(report, isBusy: uiState.busyReportId != nil)
                        }
                    }
                    .padding(16)
                }
            }
        } else {
            VStack(spacing: 8) {
                Text(uiState.loadError ?? "신고 목록을 불러오지 못했습니다.")
                    .font(.subheadline)
                    .foregroundColor(colors.rust)
                Button("다시 시도") { groupReportsViewModel.onAction(.refresh) }
                    .font(.subheadline.bold())
                    .foregroundColor(colors.accent)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    /// 신고 한 행 — 상태 뱃지+일시·신고자, 게시글 요약(탭=상세), 사유, 대기중이면 처리 버튼(웹 카드 미러)
    private func reportCard(_ report: PostReport, isBusy: Bool) -> some View {
        SGCard {
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 8) {
                    statusBadge(report.status)
                    Text("\(TimeFormats.relative(report.createdAt)) · \(report.reporterName)님 신고")
                        .font(.caption2)
                        .foregroundColor(colors.inkFaint)
                }
                Button {
                    selectedPostId = report.postId
                } label: {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("\(report.postAuthorName)님의 게시글")
                            .font(.footnote)
                            .foregroundColor(colors.inkSoft)
                        Text(report.postTextPreview.isEmpty ? "(본문 없이 첨부만 있는 게시글)" : report.postTextPreview)
                            .font(.subheadline)
                            .foregroundColor(colors.ink)
                            .multilineTextAlignment(.leading)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .buttonStyle(.plain)
                if let reason = report.reason, !reason.isEmpty {
                    Text("신고 사유: \(reason)")
                        .font(.footnote)
                        .foregroundColor(colors.inkSoft)
                }
                if report.status == .pending {
                    HStack(spacing: 8) {
                        Button("확인 처리") { groupReportsViewModel.onAction(.process(report.id, .resolved)) }
                            .font(.subheadline.bold())
                            .foregroundColor(colors.inkSoft)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background(
                                RoundedRectangle(cornerRadius: colors.radiusButton ?? 12, style: .continuous)
                                    .stroke(colors.stoneBorder, lineWidth: 1)
                            )
                            .buttonStyle(.plain)
                            .disabled(isBusy)
                        Button("기각") { groupReportsViewModel.onAction(.process(report.id, .dismissed)) }
                            .font(.subheadline.bold())
                            .foregroundColor(colors.inkSoft)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .buttonStyle(.plain)
                            .disabled(isBusy)
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
        }
    }

    /// 상태 pill — 웹 신고함의 색 테두리 뱃지 미러.
    /// Kotlin enum은 Swift enum이 아니라 switch 패턴 매칭 대신 ==로 가른다(myRole == .owner 선례)
    private func statusBadge(_ status: ReportStatus) -> some View {
        let label = status == .pending ? "대기중" : (status == .resolved ? "확인됨" : "기각됨")
        let color = status == .pending ? colors.accent : (status == .resolved ? colors.moss : colors.inkFaint)

        return Text(label)
            .font(.caption2.bold())
            .foregroundColor(color)
            .padding(.horizontal, 8)
            .padding(.vertical, 1)
            .overlay(
                RoundedRectangle(cornerRadius: 999, style: .continuous)
                    .stroke(color, lineWidth: 1)
            )
    }

    /// 필터 칩 — 활성이면 accent 채움(웹 필터 칩 미러)
    private func filterChip(_ label: String, active: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(.subheadline.bold())
                .foregroundColor(active ? colors.onAccent : colors.inkSoft)
                .padding(.horizontal, 12)
                .padding(.vertical, 5)
                .background(
                    RoundedRectangle(cornerRadius: 999, style: .continuous)
                        .fill(active ? colors.accent : Color.clear)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 999, style: .continuous)
                        .stroke(active ? colors.accent : colors.stoneBorder, lineWidth: 1)
                )
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder private var postDetailDestination: some View {
        if let postId = selectedPostId {
            PostDetailView(
                groupId: groupId,
                postId: postId,
                chatViewModel: chatViewModel,
                profileViewModel: profileViewModel
            )
        }
    }

    /// pop(백 버튼/스와이프) 시 selectedPostId를 nil로 되돌리는 브리지(MainShellView 선례)
    private var showPostDetail: Binding<Bool> {
        Binding(
            get: { selectedPostId != nil },
            set: { if !$0 { selectedPostId = nil } }
        )
    }

    init(groupId: Int64, chatViewModel: ChatViewModel, profileViewModel: ProfileViewModel) {
        self.groupId = groupId
        self.chatViewModel = chatViewModel
        self.profileViewModel = profileViewModel
        _groupReportsViewModel = StateObject(wrappedValue: GroupReportsViewModel(groupId: groupId))
    }
}
