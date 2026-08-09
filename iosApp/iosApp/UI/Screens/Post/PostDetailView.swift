import Shared
import SwiftUI

/// 게시글 상세 — composeApp PostDetailScreen.kt와 1:1 미러.
/// 본문·이미지·좋아요·댓글(답글 포함). 삭제 성공은 화면이 수집해 onDeleted로 알린다
/// (호출부가 복귀+피드 갱신을 처리한다 — CreatePostView와 같은 규약).
struct PostDetailView: View {
    let onDeleted: () -> Void

    // 수정 화면을 push할 때 다시 필요하다
    private let container: AppContainer

    private let groupId: Int64

    private let postId: Int64

    @Environment(\.sgColors) private var colors

    @Environment(\.dismiss) private var dismiss

    @StateObject private var postDetailViewModel: PostDetailViewModel

    @State private var commentText = ""

    /// 더보기 메뉴에서 고른 "수정" — 메뉴 안에서는 NavigationLink가 동작하지 않아 상태로 push한다
    @State private var showEdit = false

    /// 되돌릴 수 없는 액션은 확인을 받는다(웹 confirm 미러)
    @State private var confirmAction: ConfirmAction?

    /// 수정 화면 push — NavigationStack은 iOS 16+라 iOS 15는 숨김 NavigationLink 폴백(그룹 상세 미러)
    var body: some View {
        if #available(iOS 16.0, *) {
            core.navigationDestination(isPresented: $showEdit) { editDestination }
        } else {
            core.background(
                NavigationLink(isActive: $showEdit) {
                    editDestination
                } label: {
                    EmptyView()
                }
                .hidden()
            )
        }
    }

    private var editDestination: some View {
        CreatePostView(container: container, groupId: groupId, postId: postId) {
            // 수정하고 돌아오면 바뀐 본문을 보여줘야 한다.
            // 목록은 갱신 신호를 받지 않는다 — 수정 알림(ObservePostUpdatesUseCase)을 받은 목록 VM이
            // 자기 스냅샷에서 그 항목만 갈아끼운다(refresh를 태우면 첫 페이지부터 전체 재조회가 된다)
            postDetailViewModel.onAction(.reload)
        }
    }

    @ViewBuilder private var core: some View {
        let uiState = postDetailViewModel.uiState

        VStack(spacing: 0) {
            if let message = uiState.notice {
                HStack {
                    Text(message).font(.caption).foregroundColor(colors.moss)
                    Spacer()
                    Button("닫기") { postDetailViewModel.onAction(.clearNotice) }
                        .font(.caption.bold())
                        .foregroundColor(colors.accent)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .background(colors.linen)
            }

            if let message = uiState.error {
                HStack {
                    Text(message).font(.caption).foregroundColor(colors.ink)
                    Spacer()
                    Button("닫기") { postDetailViewModel.onAction(.clearError) }
                        .font(.caption.bold())
                        .foregroundColor(colors.accent)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .background(colors.accentSoft)
            }

            if uiState.post == nil, uiState.isLoading {
                Spacer()
                ProgressView().tint(colors.accent)
                Spacer()
            } else if uiState.post == nil {
                Spacer()
                Button("다시 시도") { postDetailViewModel.onAction(.reload) }
                    .foregroundColor(colors.accent)
                Spacer()
            } else {
                ScrollView {
                    // 항목 간격을 작게 잡는다 — 본문 블록과 댓글 행이 각자 세로 패딩을 갖고 있어
                    // 여기서 12를 더 주면 좋아요 행과 구분선 사이가 두 배로 벌어진다(Compose 미러).
                    LazyVStack(alignment: .leading, spacing: 4) {
                        postBody(uiState)
                        Divider().background(colors.stoneBorder)
                        Text("댓글 \(uiState.comments.count)")
                            .font(.subheadline.bold())
                            .foregroundColor(colors.inkSoft)
                            .padding(.horizontal, 16)
                        ForEach(uiState.topLevelComments, id: \.id) { comment in
                            commentRow(comment, isMine: comment.userId == uiState.myUserId, canReply: true)
                            // 답글은 한 단계만 들여쓴다(서버가 답글의 답글을 허용하지 않는다)
                            ForEach(uiState.repliesOf(comment.id), id: \.id) { reply in
                                commentRow(reply, isMine: reply.userId == uiState.myUserId, canReply: false)
                                    .padding(.leading, 40)
                            }
                        }
                    }
                    .padding(.bottom, 16)
                }
            }

            composer(uiState)
        }
        .background(colors.paper)
        .overlay {
            if let action = confirmAction {
                ActionConfirmDialog(
                    title: action == .report ? "게시글 신고" : "사용자 차단",
                    message: action == .report
                        ? "이 게시글을 신고할까요?\n접수된 신고는 그룹 관리자가 확인합니다."
                        : "\(uiState.post?.authorName ?? "")님을 차단할까요?\n차단하면 이 사용자의 글·댓글이 내 화면에서 숨겨지고 DM이 막힙니다.",
                    confirmText: action == .report ? "신고" : "차단",
                    isLoading: action == .report ? uiState.isReporting : uiState.isBlocking,
                    onDismiss: { confirmAction = nil },
                    onConfirm: {
                        confirmAction = nil
                        postDetailViewModel.onAction(action == .report ? .reportPost : .blockAuthor)
                    }
                )
            }
        }
        .navigationTitle("게시글")
        .navigationBarTitleDisplayMode(.inline)
        // ⚠️조건 분기는 ToolbarItem "안"에 둔다 — ToolbarContentBuilder의 buildIf는 iOS 16+라
        // .toolbar { if ... } 는 배포 타깃 15.0에서 컴파일되지 않는다(GroupDetailView와 같은 형태)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                // 더보기는 항상 노출하고 메뉴 내용만 갈린다 — 내 글이면 수정·삭제,
                // 남의 글이면 신고·차단(Compose DropdownMenu와 미러). 권한은 서버가 판정한다.
                // 파괴적 항목은 destructive 역할로 빨갛게 — Compose는 같은 자리를 sg.rust로 칠한다
                Menu {
                    if uiState.isMyPost {
                        Button("수정") { showEdit = true }
                        Button(role: .destructive) {
                            postDetailViewModel.onAction(.deletePost)
                        } label: {
                            Text("삭제")
                        }
                    } else {
                        Button(role: .destructive) {
                            confirmAction = .report
                        } label: {
                            Text("신고하기")
                        }
                        Button(role: .destructive) {
                            confirmAction = .block
                        } label: {
                            Text("차단하기")
                        }
                        // 글이 아직 안 실렸으면 작성자를 모르므로 차단할 수 없다
                        .disabled(uiState.post == nil)
                    }
                } label: {
                    Image(systemName: "ellipsis.circle")
                }
                .disabled(uiState.isDeletingPost || uiState.isBlocking)
            }
        }
        .onReceive(postDetailViewModel.event) { event in
            switch event {
            case .postDeleted:
                onDeleted()
                dismiss()
            // 차단하면 그 사용자의 글이 목록에서도 사라진다 — 삭제와 같은 복귀·갱신 경로
            case .authorBlocked:
                onDeleted()
                dismiss()
            // 등록에 성공했을 때만 입력창을 비운다 — 실패하면 쓴 글이 남아 재시도할 수 있다
            case .commentCreated:
                commentText = ""
            }
        }
    }

    @ViewBuilder
    private func postBody(_ uiState: PostDetailViewModel.UiState) -> some View {
        if let post = uiState.post {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 10) {
                    SGAvatar(name: post.authorName, imageUrl: post.authorProfileImg)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(post.authorName).font(.subheadline.bold()).foregroundColor(colors.ink)
                        Text(TimeFormats.relative(post.createdAt)).font(.caption).foregroundColor(colors.inkFaint)
                    }
                    Spacer()
                }
                if !post.text.isEmpty {
                    Text(post.text).font(.body).foregroundColor(colors.ink)
                }
                ForEach(post.imageUrls, id: \.self) { url in
                    AsyncImage(url: URL(string: url)) { image in
                        image.resizable().scaledToFit()
                    } placeholder: {
                        Color.clear
                    }
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                HStack(spacing: 16) {
                    Button {
                        postDetailViewModel.onAction(.toggleLike)
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: uiState.isLiked ? "heart.fill" : "heart")
                                .foregroundColor(uiState.isLiked ? colors.accent : colors.inkFaint)
                            Text("좋아요 \(uiState.likeCount)").font(.caption).foregroundColor(colors.inkSoft)
                        }
                    }
                    .disabled(uiState.isTogglingLike)
                    Text("댓글 \(uiState.comments.count)").font(.caption).foregroundColor(colors.inkSoft)
                    Spacer()
                }
            }
            // 아래쪽만 좁게 — Compose는 IconButton의 48dp 터치 영역이 여백을 대신하지만
            // SwiftUI Button은 내용 크기 그대로라 최소한의 숨 쉴 틈만 남긴다.
            .padding([.top, .leading, .trailing], 16)
            .padding(.bottom, 8)
        }
    }

    private func commentRow(_ comment: Comment, isMine: Bool, canReply: Bool) -> some View {
        HStack(alignment: .top, spacing: 8) {
            SGAvatar(name: comment.authorName, size: 28, imageUrl: comment.authorProfileImg)
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(comment.authorName).font(.caption.bold()).foregroundColor(colors.ink)
                    Text(TimeFormats.relative(comment.createdAt)).font(.caption2).foregroundColor(colors.inkFaint)
                }
                Text(comment.text).font(.subheadline).foregroundColor(colors.ink)
                HStack(spacing: 12) {
                    if canReply {
                        Button("답글") { postDetailViewModel.onAction(.setReplyTo(comment: comment)) }
                            .font(.caption2)
                            .foregroundColor(colors.inkFaint)
                    }
                    if isMine {
                        Button("삭제") { postDetailViewModel.onAction(.deleteComment(commentId: comment.id)) }
                            .font(.caption2)
                            .foregroundColor(colors.inkFaint)
                    }
                }
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
    }

    @ViewBuilder
    private func composer(_ uiState: PostDetailViewModel.UiState) -> some View {
        VStack(spacing: 0) {
            Divider().background(colors.stoneBorder)
            // 답글 대상이 정해지면 누구에게 다는지 보여주고, 그 자리에서 취소할 수 있게 한다
            if let target = uiState.replyTo {
                HStack {
                    Text("\(target.authorName)님에게 답글").font(.caption).foregroundColor(colors.ink)
                    Spacer()
                    Button {
                        postDetailViewModel.onAction(.setReplyTo(comment: nil))
                    } label: {
                        Image(systemName: "xmark").foregroundColor(colors.inkFaint)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 6)
                .background(colors.accentSoft)
            }
            HStack(spacing: 8) {
                // 라벨 없이 입력창만 — SGTextField의 label은 필드 위에 별도 줄로 그려져서
                // 한 줄짜리 댓글 입력에는 군더더기다(답글 대상은 위 칩이 이미 알려준다).
                SGTextField(text: $commentText)
                Button {
                    postDetailViewModel.onAction(.submitComment(text: commentText))
                } label: {
                    Image(systemName: "paperplane.fill")
                        .foregroundColor(
                            uiState.isSubmittingComment || commentText.isEmpty ? colors.inkFaint : colors.accent
                        )
                }
                .disabled(uiState.isSubmittingComment || commentText.isEmpty)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
        }
        .background(colors.paper)
    }
    
    init(container: AppContainer, groupId: Int64, postId: Int64, onDeleted: @escaping () -> Void) {
        self.onDeleted = onDeleted
        self.container = container
        self.groupId = groupId
        self.postId = postId
        _postDetailViewModel = StateObject(wrappedValue: PostDetailViewModel(
            groupId: groupId,
            postId: postId,
            getPostDetailUseCase: container.getPostDetailUseCase,
            setPostLikedUseCase: container.setPostLikedUseCase,
            createCommentUseCase: container.createCommentUseCase,
            deleteCommentUseCase: container.deleteCommentUseCase,
            deletePostUseCase: container.deletePostUseCase,
            reportPostUseCase: container.reportPostUseCase,
            blockUserUseCase: container.blockUserUseCase,
            getCurrentUserIdUseCase: container.getCurrentUserIdUseCase
        ))
    }
}

/// 더보기 메뉴의 되돌릴 수 없는 액션 — 확인 다이얼로그를 한 번 거친다(Compose ConfirmAction 미러)
private enum ConfirmAction {
    case report
    case block
}

/// 신고·차단 확인 다이얼로그 — Compose ActionConfirmDialog 미러(GroupDetailView의 DmConfirmDialog와
/// 같은 반투명 배경+중앙 카드). 실패 메시지는 다이얼로그가 아니라 화면 상단 에러 배너에 뜬다.
private struct ActionConfirmDialog: View {
    let title: String

    let message: String

    let confirmText: String

    let isLoading: Bool

    let onDismiss: () -> Void

    let onConfirm: () -> Void

    @Environment(\.sgColors) private var colors

    var body: some View {
        ZStack {
            Color.black.opacity(0.35)
                .ignoresSafeArea()
                .onTapGesture(perform: onDismiss)
            SGCard {
                VStack(alignment: .leading, spacing: 12) {
                    Text(title)
                        .font(.headline)
                        .foregroundColor(colors.ink)
                    Text(message)
                        .font(.subheadline)
                        .foregroundColor(colors.ink)
                    HStack(spacing: 8) {
                        Button(action: onDismiss) {
                            Text("취소")
                                .font(.subheadline)
                                .frame(maxWidth: .infinity)
                                // SGPrimaryButton과 같은 높이로 나란히 맞춘다
                                .frame(height: 48)
                                .overlay(
                                    RoundedRectangle(cornerRadius: colors.radiusButton ?? 20, style: .continuous)
                                        .stroke(colors.stoneBorder, lineWidth: 1)
                                )
                                .foregroundColor(colors.ink)
                        }
                        .buttonStyle(.plain)
                        SGPrimaryButton(title: confirmText, isLoading: isLoading, action: onConfirm)
                    }
                }
                .padding(16)
            }
            .padding(24)
        }
    }
}
