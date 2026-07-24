import SwiftUI
import Paging
import Shared

/// 알림 — 웹 /notifications·Compose NotificationsScreen 미러: 미읽음 헤더(N건+모두 읽음 처리)+타입 라벨 목록.
/// 서버 응답엔 행위자/본문이 없어 타입 라벨+상대시각만 그리고, 클릭 이동도 웹처럼 아직 없다.
/// 셸 목적지라 내비바는 셸이 소유. 계층은 Compose와 1:1 — View=상태 소유(VM 선언), Content=구독+UI.
struct NotificationsView: View {
    @StateObject private var viewModel: NotificationsViewModel

    var body: some View {
        NotificationsContent(viewModel: viewModel)
    }

    init(container: AppContainer) {
        _viewModel = StateObject(wrappedValue: NotificationsViewModel(container: container))
    }
}

private struct NotificationsContent: View {
    @ObservedObject var viewModel: NotificationsViewModel

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
            ScrollView {
                VStack(spacing: 12) {
                    content
                }
                .padding(.horizontal, 16)
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
                    notificationCard(notification)
                }
            }
            SGPagingFooter(
                error: appendState is LoadState.Error ? "알림을 더 불러오지 못했습니다." : nil,
                isLoadingMore: appendState is LoadState.Loading,
                onRetry: { lazyPagingItems.retry() }
            )
        }
    }

    /// 웹 notification-list 아이템 미러 — 타입 라벨+상대시각, 읽음은 흐리게, 미읽음에만 읽음 버튼
    private func notificationCard(_ notification: AppNotification) -> some View {
        let isRead = viewModel.uiState.isRead(notification)
        // VM이 한 건씩만 처리하므로 처리 중엔 모든 행의 버튼을 잠근다
        let enabled = viewModel.uiState.processingId == nil
        let isProcessing = viewModel.uiState.processingId == notification.id
        return SGCard {
            HStack(spacing: 8) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(typeLabel(notification.type))
                        .font(.subheadline.bold())
                        .foregroundColor(colors.ink)
                    Text(TimeFormats.relative(notification.createdAt))
                        .font(.caption2)
                        .foregroundColor(colors.inkFaint)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if !isRead {
                    Button {
                        viewModel.onAction(.markAsRead(notificationId: notification.id))
                    } label: {
                        HStack(spacing: 6) {
                            if isProcessing {
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: colors.inkFaint))
                                    .scaleEffect(0.7)
                            }
                            Text("읽음").font(.subheadline)
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                        .overlay(
                            RoundedRectangle(cornerRadius: colors.radiusButton ?? 20, style: .continuous)
                                .stroke(colors.stoneBorder, lineWidth: 1)
                        )
                        .foregroundColor(enabled ? colors.ink : colors.inkFaint)
                    }
                    .disabled(!enabled)
                }
            }
            .padding(12)
        }
        .opacity(isRead ? 0.6 : 1)
    }

    init(viewModel: NotificationsViewModel) {
        // Compose와 동일: 상태에서 pagingData만 뽑아낸 스트림을 collectAsLazyPagingItems로 수집
        let pagingDataPublisher = viewModel.$uiState.map { $0.pagingData }.removeDuplicates { $0 === $1 }

        self.viewModel = viewModel
        _lazyPagingItems = StateObject(wrappedValue: pagingDataPublisher.collectAsLazyPagingItems())
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
