package kr.hhp227.storygroup.ui.screens.post

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.shared.domain.model.Comment
import kr.hhp227.storygroup.shared.domain.model.Post
import kr.hhp227.storygroup.shared.domain.usecase.BlockUserUseCase
import kr.hhp227.storygroup.shared.domain.usecase.CreateCommentUseCase
import kr.hhp227.storygroup.shared.domain.usecase.DeleteCommentUseCase
import kr.hhp227.storygroup.shared.domain.usecase.DeletePostUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetCurrentUserIdUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetPostDetailUseCase
import kr.hhp227.storygroup.shared.domain.usecase.ReportPostUseCase
import kr.hhp227.storygroup.shared.domain.usecase.ReportUserUseCase
import kr.hhp227.storygroup.shared.domain.usecase.SetPostLikedUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel

/**
 * 게시글 상세 — 본문·좋아요·댓글(답글 포함). 웹 /groups/{id}/posts/{postId} 미러.
 * 진입 시 스스로 로드한다(피드가 넘겨준 값을 쓰지 않는다 — 그 사이 수정·삭제됐을 수 있다).
 * 삭제 성공은 Event.PostDeleted 일회성 발화 — 화면은 닫기만 하고, 목록에서 그 글을 걷어내는 일은
 * 피드 VM이 삭제 알림(ObservePostDeletionsUseCase)을 받아 스냅샷에서 처리한다.
 * 남의 글이면 신고·차단을 할 수 있다(웹 게시글 상세 미러) — 차단은 그 글이 목록에서 사라지므로
 * 삭제와 같은 복귀·갱신 경로(Event.AuthorBlocked)를 탄다.
 * 댓글도 같은 메뉴를 갖는다 — 댓글 신고 API는 없어 작성자를 신고하고(웹 UserActionMenu 미러),
 * 차단하면 그 작성자의 댓글을 목록에서 바로 걷어낸다(서버 숨김과 같은 결과).
 * 피드에서 그 작성자의 글을 걷어내는 건 각 피드 VM이 차단 알림을 받아 처리한다.
 * iosApp PostDetailViewModel.swift와 1:1 미러
 */
class PostDetailViewModel(
    private val groupId: Long,
    private val postId: Long,
    private val getPostDetailUseCase: GetPostDetailUseCase,
    private val setPostLikedUseCase: SetPostLikedUseCase,
    private val createCommentUseCase: CreateCommentUseCase,
    private val deleteCommentUseCase: DeleteCommentUseCase,
    private val deletePostUseCase: DeletePostUseCase,
    private val reportPostUseCase: ReportPostUseCase,
    private val reportUserUseCase: ReportUserUseCase,
    private val blockUserUseCase: BlockUserUseCase,
    getCurrentUserIdUseCase: GetCurrentUserIdUseCase
) : ViewModel(), MviViewModel<PostDetailViewModel.UiState, PostDetailViewModel.Action, PostDetailViewModel.Event> {
    private val myUserId = getCurrentUserIdUseCase()

    private val _uiState = MutableStateFlow(UiState(myUserId = myUserId))
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<Event>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val event: Flow<Event> = _event.asSharedFlow()

    init {
        load()
    }

    override fun onAction(action: Action) {
        when (action) {
            Action.Reload -> load()
            Action.ToggleLike -> toggleLike()
            is Action.SubmitComment -> submitComment(action.text)
            is Action.SetReplyTo -> _uiState.update { it.copy(replyTo = action.comment) }
            is Action.DeleteComment -> deleteComment(action.commentId)
            Action.DeletePost -> deletePost()
            Action.ReportPost -> reportPost()
            Action.BlockAuthor -> blockAuthor()
            is Action.ReportCommentAuthor -> reportCommentAuthor(action.userId)
            is Action.BlockCommentAuthor -> blockCommentAuthor(action.userId)
            Action.ClearError -> _uiState.update { it.copy(error = null) }
            Action.ClearNotice -> _uiState.update { it.copy(notice = null) }
        }
    }

    private fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { getPostDetailUseCase(groupId, postId) }
                .onSuccess { detail ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            post = detail.post,
                            likeCount = detail.likes.size,
                            isLiked = detail.likes.any { like -> like.userId == myUserId },
                            comments = detail.comments
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "게시글을 불러오지 못했습니다.") }
                }
        }
    }

    private fun toggleLike() {
        val state = _uiState.value

        if (state.isTogglingLike || state.post == null) return
        val next = !state.isLiked

        // 누른 즉시 반영하고 서버 응답으로 확정한다 — 실패하면 되돌린다.
        _uiState.update {
            it.copy(
                isTogglingLike = true,
                isLiked = next,
                likeCount = (it.likeCount + if (next) 1 else -1).coerceAtLeast(0)
            )
        }
        viewModelScope.launch {
            runCatching { setPostLikedUseCase(groupId, postId, next) }
                .onSuccess { likes ->
                    _uiState.update {
                        it.copy(
                            isTogglingLike = false,
                            likeCount = likes.size,
                            isLiked = likes.any { like -> like.userId == myUserId }
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isTogglingLike = false,
                            isLiked = !next,
                            likeCount = (it.likeCount + if (next) -1 else 1).coerceAtLeast(0),
                            error = e.message ?: "좋아요 처리에 실패했습니다."
                        )
                    }
                }
        }
    }

    private fun submitComment(text: String) {
        if (_uiState.value.isSubmittingComment) return
        if (text.isBlank()) {
            _uiState.update { it.copy(error = "댓글 내용을 입력해주세요.") }
            return
        }
        val parentReplyId = _uiState.value.replyTo?.id

        _uiState.update { it.copy(isSubmittingComment = true, error = null) }
        viewModelScope.launch {
            runCatching { createCommentUseCase(groupId, postId, text, parentReplyId) }
                .onSuccess { created ->
                    _uiState.update {
                        it.copy(isSubmittingComment = false, comments = it.comments + created, replyTo = null)
                    }
                    _event.tryEmit(Event.CommentCreated)
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isSubmittingComment = false, error = e.message ?: "댓글 작성에 실패했습니다.") }
                }
        }
    }

    private fun deleteComment(commentId: Long) {
        viewModelScope.launch {
            runCatching { deleteCommentUseCase(groupId, postId, commentId) }
                .onSuccess {
                    // 답글도 같이 사라진다(서버가 자식까지 지운다) — 목록에서도 같은 규칙으로 걷어낸다.
                    _uiState.update { state ->
                        state.copy(
                            comments = state.comments.filterNot { it.id == commentId || it.parentReplyId == commentId },
                            replyTo = state.replyTo?.takeIf { it.id != commentId }
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message ?: "댓글 삭제에 실패했습니다.") }
                }
        }
    }

    private fun deletePost() {
        if (_uiState.value.isDeletingPost) return

        _uiState.update { it.copy(isDeletingPost = true, error = null) }
        viewModelScope.launch {
            runCatching { deletePostUseCase(groupId, postId) }
                .onSuccess {
                    _uiState.update { it.copy(isDeletingPost = false) }
                    _event.tryEmit(Event.PostDeleted)
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isDeletingPost = false, error = e.message ?: "게시글 삭제에 실패했습니다.") }
                }
        }
    }

    private fun reportPost() {
        if (_uiState.value.isReporting) return

        _uiState.update { it.copy(isReporting = true, error = null, notice = null) }
        viewModelScope.launch {
            runCatching { reportPostUseCase(groupId, postId) }
                .onSuccess {
                    // 화면에서 달라지는 게 없으므로 접수됐다는 안내가 유일한 피드백이다(웹 미러)
                    _uiState.update { it.copy(isReporting = false, notice = "신고가 접수되었습니다.") }
                }
                .onFailure { e ->
                    // 이미 대기중 신고가 있으면 409 — 서버 메시지를 그대로 보여준다
                    _uiState.update { it.copy(isReporting = false, error = e.message ?: "신고에 실패했습니다.") }
                }
        }
    }

    private fun blockAuthor() {
        val authorId = _uiState.value.post?.userId ?: return

        if (_uiState.value.isBlocking) return
        _uiState.update { it.copy(isBlocking = true, error = null, notice = null) }
        viewModelScope.launch {
            runCatching { blockUserUseCase(authorId) }
                .onSuccess {
                    _uiState.update { it.copy(isBlocking = false) }
                    _event.tryEmit(Event.AuthorBlocked)
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isBlocking = false, error = e.message ?: "차단에 실패했습니다.") }
                }
        }
    }

    private fun reportCommentAuthor(userId: Long) {
        if (_uiState.value.isReporting) return

        _uiState.update { it.copy(isReporting = true, error = null, notice = null) }
        viewModelScope.launch {
            runCatching { reportUserUseCase(userId) }
                .onSuccess {
                    _uiState.update { it.copy(isReporting = false, notice = "신고가 접수되었습니다.") }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isReporting = false, error = e.message ?: "신고에 실패했습니다.") }
                }
        }
    }

    private fun blockCommentAuthor(userId: Long) {
        if (_uiState.value.isBlocking) return

        _uiState.update { it.copy(isBlocking = true, error = null, notice = null) }
        viewModelScope.launch {
            runCatching { blockUserUseCase(userId) }
                .onSuccess {
                    // 서버는 다음 조회부터 이 사람의 댓글을 숨긴다 — 화면에선 지금 바로 걷어낸다(웹 미러).
                    // 답글 대상이었다면 함께 해제한다(사라진 댓글에 답글을 달 수 없다)
                    _uiState.update { state ->
                        state.copy(
                            isBlocking = false,
                            notice = "차단했습니다.",
                            comments = state.comments.filterNot { it.userId == userId },
                            replyTo = state.replyTo?.takeIf { it.userId != userId }
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isBlocking = false, error = e.message ?: "차단에 실패했습니다.") }
                }
        }
    }

    data class UiState(
        val isLoading: Boolean = false,
        val error: String? = null,
        /** 완료 안내(신고 접수 등) — 에러와 같은 자리에 다른 톤으로 그린다 */
        val notice: String? = null,
        val post: Post? = null,
        val likeCount: Int = 0,
        val isLiked: Boolean = false,
        val isTogglingLike: Boolean = false,
        val comments: List<Comment> = emptyList(),
        /** 답글 대상 — null이면 최상위 댓글로 달린다 */
        val replyTo: Comment? = null,
        val isSubmittingComment: Boolean = false,
        val isDeletingPost: Boolean = false,
        val isReporting: Boolean = false,
        val isBlocking: Boolean = false,
        val myUserId: Long? = null
    ) {
        val isMyPost: Boolean get() = post != null && post.userId == myUserId

        /** 최상위 댓글 목록(작성 순) */
        val topLevelComments: List<Comment> get() = comments.filter { it.parentReplyId == null }

        /** 특정 댓글의 답글 목록 */
        fun repliesOf(commentId: Long): List<Comment> = comments.filter { it.parentReplyId == commentId }
    }

    sealed interface Action {
        data object Reload : Action
        data object ToggleLike : Action
        data class SubmitComment(val text: String) : Action
        data class SetReplyTo(val comment: Comment?) : Action
        data class DeleteComment(val commentId: Long) : Action
        data object DeletePost : Action

        /** 게시글 신고 — 남의 글에만 노출된다(권한 판정은 서버) */
        data object ReportPost : Action

        /** 작성자 차단 — 남의 글에만 노출된다 */
        data object BlockAuthor : Action

        /** 댓글 작성자 신고 — 댓글 신고 API가 없어 사용자 신고로 접수한다 */
        data class ReportCommentAuthor(val userId: Long) : Action

        /** 댓글 작성자 차단 — 그 작성자의 댓글을 목록에서 걷어낸다 */
        data class BlockCommentAuthor(val userId: Long) : Action
        data object ClearError : Action
        data object ClearNotice : Action
    }

    sealed interface Event {
        data object PostDeleted : Event

        /**
         * 게시글 작성자 차단 성공 — 화면만 닫는다. 목록에서 그 사람의 글을 걷어내는 일은
         * 피드 VM이 차단 알림(ObserveUserBlocksUseCase)을 받아 스냅샷에서 처리한다.
         */
        data object AuthorBlocked : Event

        data object CommentCreated : Event
    }
}
