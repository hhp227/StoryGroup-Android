import Combine
import Foundation
import Shared

/// 게시글 상세 — composeApp PostDetailViewModel.kt와 1:1 미러.
/// 진입 시 스스로 로드한다(피드가 넘겨준 값을 쓰지 않는다 — 그 사이 수정·삭제됐을 수 있다).
/// 삭제 성공은 Event.postDeleted 일회성 발화 — 호출부가 복귀+피드 갱신을 처리한다.
final class PostDetailViewModel: MviViewModel {
    @Published private(set) var uiState = UiState()

    let event = PassthroughSubject<Event, Never>()

    private let groupId: Int64

    private let postId: Int64

    private let getPostDetailUseCase: GetPostDetailUseCase

    private let setPostLikedUseCase: SetPostLikedUseCase

    private let createCommentUseCase: CreateCommentUseCase

    private let deleteCommentUseCase: DeleteCommentUseCase

    private let deletePostUseCase: DeletePostUseCase

    private let myUserId: Int64?

    func onAction(_ action: Action) {
        switch action {
        case .reload: load()
        case .toggleLike: toggleLike()
        case .submitComment(let text): submitComment(text: text)
        case .setReplyTo(let comment): uiState.replyTo = comment
        case .deleteComment(let commentId): deleteComment(commentId: commentId)
        case .deletePost: deletePost()
        case .clearError: uiState.error = nil
        }
    }

    private func load() {
        uiState.isLoading = true
        uiState.error = nil
        Task { @MainActor in
            do {
                let detail = try await getPostDetailUseCase.invoke(groupId: groupId, postId: postId)
                uiState.isLoading = false
                uiState.post = detail.post
                uiState.likeCount = detail.likes.count
                uiState.isLiked = detail.likes.contains { $0.userId == myUserId }
                uiState.comments = detail.comments
            } catch {
                uiState.isLoading = false
                uiState.error = error.kotlinMessage(fallback: "게시글을 불러오지 못했습니다.")
            }
        }
    }

    private func toggleLike() {
        if uiState.isTogglingLike || uiState.post == nil { return }
        let next = !uiState.isLiked

        // 누른 즉시 반영하고 서버 응답으로 확정한다 — 실패하면 되돌린다.
        uiState.isTogglingLike = true
        uiState.isLiked = next
        uiState.likeCount = max(0, uiState.likeCount + (next ? 1 : -1))
        Task { @MainActor in
            do {
                let likes = try await setPostLikedUseCase.invoke(groupId: groupId, postId: postId, liked: next)
                uiState.isTogglingLike = false
                uiState.likeCount = likes.count
                uiState.isLiked = likes.contains { $0.userId == myUserId }
            } catch {
                uiState.isTogglingLike = false
                uiState.isLiked = !next
                uiState.likeCount = max(0, uiState.likeCount + (next ? -1 : 1))
                uiState.error = error.kotlinMessage(fallback: "좋아요 처리에 실패했습니다.")
            }
        }
    }

    private func submitComment(text: String) {
        if uiState.isSubmittingComment { return }
        if text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            uiState.error = "댓글 내용을 입력해주세요."
            return
        }
        let parentReplyId = uiState.replyTo?.id

        uiState.isSubmittingComment = true
        uiState.error = nil
        Task { @MainActor in
            do {
                let created = try await createCommentUseCase.invoke(
                    groupId: groupId,
                    postId: postId,
                    text: text,
                    parentReplyId: parentReplyId.map { KotlinLong(value: $0) }
                )
                uiState.isSubmittingComment = false
                uiState.comments.append(created)
                uiState.replyTo = nil
                event.send(.commentCreated)
            } catch {
                uiState.isSubmittingComment = false
                uiState.error = error.kotlinMessage(fallback: "댓글 작성에 실패했습니다.")
            }
        }
    }

    private func deleteComment(commentId: Int64) {
        Task { @MainActor in
            do {
                try await deleteCommentUseCase.invoke(groupId: groupId, postId: postId, commentId: commentId)
                // 답글도 같이 사라진다(서버가 자식까지 지운다) — 목록에서도 같은 규칙으로 걷어낸다.
                uiState.comments.removeAll { $0.id == commentId || $0.parentReplyId?.int64Value == commentId }
                if uiState.replyTo?.id == commentId { uiState.replyTo = nil }
            } catch {
                uiState.error = error.kotlinMessage(fallback: "댓글 삭제에 실패했습니다.")
            }
        }
    }

    private func deletePost() {
        if uiState.isDeletingPost { return }

        uiState.isDeletingPost = true
        uiState.error = nil
        Task { @MainActor in
            do {
                try await deletePostUseCase.invoke(groupId: groupId, postId: postId)
                uiState.isDeletingPost = false
                event.send(.postDeleted)
            } catch {
                uiState.isDeletingPost = false
                uiState.error = error.kotlinMessage(fallback: "게시글 삭제에 실패했습니다.")
            }
        }
    }

    init(
        groupId: Int64,
        postId: Int64,
        getPostDetailUseCase: GetPostDetailUseCase,
        setPostLikedUseCase: SetPostLikedUseCase,
        createCommentUseCase: CreateCommentUseCase,
        deleteCommentUseCase: DeleteCommentUseCase,
        deletePostUseCase: DeletePostUseCase,
        getCurrentUserIdUseCase: GetCurrentUserIdUseCase
    ) {
        self.groupId = groupId
        self.postId = postId
        self.getPostDetailUseCase = getPostDetailUseCase
        self.setPostLikedUseCase = setPostLikedUseCase
        self.createCommentUseCase = createCommentUseCase
        self.deleteCommentUseCase = deleteCommentUseCase
        self.deletePostUseCase = deletePostUseCase
        self.myUserId = getCurrentUserIdUseCase.invoke()?.int64Value
        self.uiState.myUserId = self.myUserId
        load()
    }

    struct UiState {
        var isLoading = false
        var error: String? = nil
        var post: Post? = nil
        var likeCount = 0
        var isLiked = false
        var isTogglingLike = false
        var comments: [Comment] = []
        /// 답글 대상 — nil이면 최상위 댓글로 달린다
        var replyTo: Comment? = nil
        var isSubmittingComment = false
        var isDeletingPost = false
        var myUserId: Int64? = nil

        var isMyPost: Bool { post.map { $0.userId == myUserId } ?? false }

        /// 최상위 댓글 목록(작성 순)
        var topLevelComments: [Comment] { comments.filter { $0.parentReplyId == nil } }

        /// 특정 댓글의 답글 목록
        func repliesOf(_ commentId: Int64) -> [Comment] {
            comments.filter { $0.parentReplyId?.int64Value == commentId }
        }
    }

    enum Action {
        case reload
        case toggleLike
        case submitComment(text: String)
        case setReplyTo(comment: Comment?)
        case deleteComment(commentId: Int64)
        case deletePost
        case clearError
    }

    enum Event {
        case postDeleted
        case commentCreated
    }
}
