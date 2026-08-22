import SwiftUI
import Paging
import Shared

/// 알림 — Compose NotificationsScreen 미러: 미읽음 헤더(N건+모두 읽음 처리)+풀블리드 행 목록
/// (미읽음=linen, 행 사이 헤어라인). 서버가 target에서 역추적한 컨텍스트(그룹명·게시글 미리보기)를
/// 둘째 줄에 그린다 — 행위자는 서버가 저장하지 않아 여전히 없다. 게시글 컨텍스트가 풀린 행은
/// 탭 시 읽음 처리 후 게시글 상세로 이동(풀리지 않은 행 — 그룹류/삭제된 대상 — 은 표시만).
/// 셸 목적지라 내비바는 셸이 소유. VM도 셸(MainShellView)이 소유·주입한다 — 종 아이콘 뱃지와
/// 같은 인스턴스(Compose sessionNotificationsViewModel 미러). 계층은 Compose와 1:1.
struct NotificationsView: View {
    @ObservedObject var viewModel: NotificationsViewModel

    /// 알림 행 탭 → 게시글 상세 push — 셸이 onNavigationAction(.navigateToPostDetail)로 어댑팅해 넘긴다
    let onOpenPost: (Int64, Int64) -> Void

    var body: some View {
        NotificationsContent(viewModel: viewModel, onOpenPost: onOpenPost)
    }
}

private struct NotificationsContent: View {
    @ObservedObject var viewModel: NotificationsViewModel

    let onOpenPost: (Int64, Int64) -> Void

    /// Compose collectAsLazyPagingItems 미러 — 뷰 수명 동안 페이징 스트림 구독을 유지한다
    @StateObject private var lazyPagingItems: LazyPagingItems<AppNotification>

    @Environment(\.sgColors) private var colors

    var body: some View {
        VStack(spacing: 0) {
            // 웹 notification-list 미러 — 미읽음이 있을 때만 헤더 행 노출
            if viewModel.uiState.unreadCount > 0 {
                HStack {
                    Text("미읽음 \(viewModel.uiState.unreadCount)건")
                        .font(.caption)
                        .foregroundColor(colors.inkSoft)
                    Spacer()
                    Button("모두 읽음 처리") { viewModel.onAction(.markAllAsRead) }
                        .font(.subheadline.bold())
                        .foregroundColor(colors.accent)
                        .disabled(viewModel.uiState.isMarkingAll)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 4)
            }
            if let actionError = viewModel.uiState.actionError {
                Text(actionError)
                    .font(.caption)
                    .foregroundColor(colors.rust)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 16)
            }
            // 풀블리드 목록 — 행이 화면 폭 전체를 쓰므로 가로 패딩 없이 행 내부 패딩만 둔다
            ScrollView {
                VStack(spacing: 0) {
                    content
                }
                .padding(.vertical, 8)
            }
        }
        .background(colors.paper)
        // VM의 일회성 갱신 이벤트 — 프레젠터 refresh()가 활성 PagingSource를 무효화해
        // 같은 스트림이 새 세대(첫 페이지)를 방출한다(홈 피드와 동일 패턴)
        .onReceive(viewModel.event) { event in
            switch event {
            case .refreshList: lazyPagingItems.refresh()
            }
        }
        // 진입 시 신선화 — 미읽음 수+목록을 함께 최신화한다
        .onAppear { viewModel.onAction(.refresh) }
    }

    /// 로딩/에러/빈 상태는 Paging LoadState로 그린다(Compose NotificationsContent 미러)
    @ViewBuilder private var content: some View {
        let refreshState = lazyPagingItems.loadState.refresh
        let appendState = lazyPagingItems.loadState.append

        if lazyPagingItems.itemCount == 0, refreshState is LoadState.Loading {
            ProgressView().padding(.vertical, 48)
        } else if lazyPagingItems.itemCount == 0, refreshState is LoadState.Error {
            VStack(spacing: 8) {
                Text("알림을 불러오지 못했습니다.").font(.subheadline).foregroundColor(colors.rust)
                Button("다시 시도") { lazyPagingItems.retry() }
                    .font(.subheadline)
                    .foregroundColor(colors.accent)
            }
            .padding(.vertical, 48)
        } else if lazyPagingItems.itemCount == 0 {
            SGEmptyState(
                title: "알림이 없습니다",
                subtitle: "새 소식이 생기면 여기에 표시됩니다.",
                systemImage: "bell.fill"
            )
            .padding(.vertical, 48)
        } else {
            ForEach(lazyPagingItems, key: { AnyHashable($0.id) }) { notification in
                if let notification {
                    notificationRow(notification)
                }
            }
            SGPagingFooter(
                error: appendState is LoadState.Error ? "알림을 더 불러오지 못했습니다." : nil,
                isLoadingMore: appendState is LoadState.Loading,
                onRetry: { lazyPagingItems.retry() }
            )
            .padding(.horizontal, 16)
        }
    }

    /// 알림 한 행(풀블리드) — 타입 아이콘 메달리온+라벨+컨텍스트(그룹명 · 게시글 미리보기)+상대시각,
    /// 미읽음=linen 배경·읽음=흐리게, 행 아래 헤어라인. 미읽음에만 읽음 버튼.
    /// 게시글 컨텍스트가 풀린 행만 탭 가능 — 읽음 처리(가능할 때) 후 게시글 상세로 이동
    /// (Compose NotificationRow 미러)
    private func notificationRow(_ notification: AppNotification) -> some View {
        let isRead = viewModel.uiState.isRead(notification)
        // VM이 한 건씩만 처리하므로 처리 중엔 모든 행의 버튼을 잠근다
        let enabled = viewModel.uiState.processingId == nil
        let isProcessing = viewModel.uiState.processingId == notification.id
        let groupId = notification.groupId?.int64Value
        let postId = notification.postId?.int64Value
        return VStack(spacing: 0) {
            HStack(spacing: 12) {
                ZStack {
                    Circle().fill(colors.accentSoft)
                    Image(systemName: typeIcon(notification.type))
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundColor(colors.accent)
                }
                .frame(width: 36, height: 36)
                VStack(alignment: .leading, spacing: 2) {
                    Text(typeLabel(notification.type))
                        .font(.subheadline.bold())
                        .foregroundColor(colors.ink)
                    if let context = contextLine(notification) {
                        Text(context)
                            .font(.caption)
                            .foregroundColor(colors.inkSoft)
                            .lineLimit(2)
                            .multilineTextAlignment(.leading)
                    }
                    Text(TimeFormats.relative(notification.createdAt))
                        .font(.caption2)
                        .foregroundColor(colors.inkFaint)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if !isRead {
                    if isProcessing {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: colors.inkFaint))
                            .scaleEffect(0.7)
                            .padding(.horizontal, 12)
                    } else {
                        Button("읽음") { viewModel.onAction(.markAsRead(notificationId: notification.id)) }
                            .font(.subheadline)
                            .foregroundColor(enabled ? colors.accent : colors.inkFaint)
                            .disabled(!enabled)
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(isRead ? Color.clear : colors.linen)
            .opacity(isRead ? 0.6 : 1)
            // 탭=소비: 다른 건 처리 중이 아니면 읽음 처리까지 함께(Compose와 동일).
            // 안쪽 "읽음" Button이 자기 탭을 우선 소비하므로 겹치지 않는다
            .contentShape(Rectangle())
            .onTapGesture {
                guard let groupId = groupId, let postId = postId else { return }
                if !isRead && enabled { viewModel.onAction(.markAsRead(notificationId: notification.id)) }
                onOpenPost(groupId, postId)
            }
            Rectangle().fill(colors.stoneBorder).frame(height: 1)
        }
    }

    init(viewModel: NotificationsViewModel, onOpenPost: @escaping (Int64, Int64) -> Void) {
        // Compose와 동일: 상태에서 pagingData만 뽑아낸 스트림을 collectAsLazyPagingItems로 수집
        let pagingDataPublisher = viewModel.$uiState.map { $0.pagingData }.removeDuplicates { $0 === $1 }

        self.viewModel = viewModel
        self.onOpenPost = onOpenPost
        _lazyPagingItems = StateObject(wrappedValue: pagingDataPublisher.collectAsLazyPagingItems())
    }
}

/// 컨텍스트 줄 — 어떤 그룹/게시글의 알림인지. 서버가 못 푼 참조(삭제 등)는 null이라 줄째 숨긴다
/// (Compose contextLine 미러)
private func contextLine(_ notification: AppNotification) -> String? {
    if let group = notification.groupName, let preview = notification.postPreview {
        return "\(group) · \(preview)"
    }
    return notification.postPreview ?? notification.groupName
}

/// 타입 아이콘 — Compose typeIcon과 1:1 의미 매핑
private func typeIcon(_ type: NotificationType) -> String {
    switch type {
    case .theNewPost: return "doc.text"
    case .comment: return "bubble.left"
    case .like: return "heart.fill"
    case .mention: return "at"
    case .chat: return "bubble.left.and.bubble.right"
    case .meetingStarted: return "video.fill"
    case .notice: return "megaphone.fill"
    case .invite: return "envelope.fill"
    case .joinRequest: return "person.badge.plus"
    case .joinApproved: return "checkmark.circle.fill"
    case .joinRejected: return "xmark.circle.fill"
    default: return "bell.fill"
    }
}

/// 웹 TYPE_LABEL 미러 — Compose typeLabel과 동일(모르는 타입은 shared가 목록에서 걸러 default 불도달)
private func typeLabel(_ type: NotificationType) -> String {
    switch type {
    case .theNewPost: return "새 게시글"
    case .comment: return "댓글"
    case .like: return "좋아요"
    case .mention: return "멘션"
    case .chat: return "채팅 메시지"
    case .meetingStarted: return "화상회의 시작"
    case .notice: return "공지"
    case .invite: return "초대"
    case .joinRequest: return "가입 신청"
    case .joinApproved: return "가입 승인"
    case .joinRejected: return "가입 거절"
    default: return "알림"
    }
}
