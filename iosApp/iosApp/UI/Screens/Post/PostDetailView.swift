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
            getCurrentUserIdUseCase: container.getCurrentUserIdUseCase
        ))
    }

    var body: some View {
        let uiState = postDetailViewModel.uiState

        VStack(spacing: 0) {
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
        .navigationTitle("게시글")
        .navigationBarTitleDisplayMode(.inline)
        // 수정·삭제는 작성자 본인만 — 서버도 같은 규칙(requirePostOwner)이라 화면은 미리 감출 뿐이다.
        // 조건은 ToolbarItem "안"에 둔다 — ToolbarContentBuilder의 조건 분기(buildIf)는 iOS 16+라
        // .toolbar { if ... } 는 배포 타깃 15.0에서 컴파일되지 않는다(GroupDetailView와 같은 형태)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                if uiState.isMyPost {
                    HStack(spacing: 12) {
                        NavigationLink {
                            CreatePostView(container: container, groupId: groupId, postId: postId) {
                                // 수정하고 돌아오면 바뀐 본문을 보여줘야 한다
                                postDetailViewModel.onAction(.reload)
                            }
                        } label: {
                            Text("수정").foregroundColor(colors.accent)
                        }
                        Button("삭제") { postDetailViewModel.onAction(.deletePost) }
                            .foregroundColor(colors.rust)
                            .disabled(uiState.isDeletingPost)
                    }
                }
            }
        }
        .onReceive(postDetailViewModel.event) { event in
            switch event {
            case .postDeleted:
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
}
