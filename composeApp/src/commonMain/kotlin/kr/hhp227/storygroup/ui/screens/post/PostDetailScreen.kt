package kr.hhp227.storygroup.ui.screens.post

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Send
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kr.hhp227.storygroup.di.LocalAppContainer
import kr.hhp227.storygroup.shared.domain.model.Comment
import kr.hhp227.storygroup.ui.components.SgAvatar
import kr.hhp227.storygroup.ui.components.SgTextField
import kr.hhp227.storygroup.ui.components.SgTopBar
import kr.hhp227.storygroup.ui.theme.SgTheme
import kr.hhp227.storygroup.ui.util.formatRelativeTime

@Composable
private fun postDetailViewModel(groupId: Long, postId: Long): PostDetailViewModel {
    val container = LocalAppContainer.current

    return viewModel(key = "post-detail-$groupId-$postId") {
        PostDetailViewModel(
            groupId = groupId,
            postId = postId,
            getPostDetailUseCase = container.getPostDetailUseCase,
            setPostLikedUseCase = container.setPostLikedUseCase,
            createCommentUseCase = container.createCommentUseCase,
            deleteCommentUseCase = container.deleteCommentUseCase,
            deletePostUseCase = container.deletePostUseCase,
            getCurrentUserIdUseCase = container.getCurrentUserIdUseCase
        )
    }
}

/**
 * 게시글 상세 — 본문·이미지·좋아요·댓글(답글 포함). 웹 /groups/{id}/posts/{postId} 미러.
 * NavHost 풀스크린 목적지라 상단바는 화면이 소유하고, 삭제 성공은 화면이 수집해 onDeleted로 알린다
 * (호출부가 복귀+피드 갱신을 처리한다 — CreatePostScreen과 같은 규약).
 * iosApp PostDetailView.swift와 1:1 미러
 */
@Composable
fun PostDetailScreen(
    groupId: Long,
    postId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
    // 수정 화면에서 돌아왔다는 신호 — 본문이 바뀌었으니 다시 읽는다(그룹 상세와 같은 규약)
    refreshRequested: Boolean = false,
    onRefreshHandled: () -> Unit = {},
    viewModel: PostDetailViewModel = postDetailViewModel(groupId, postId)
) {
    val uiState by viewModel.uiState.collectAsState()
    val onAction = viewModel::onAction
    val sg = SgTheme.colors
    var commentText by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(refreshRequested) {
        if (refreshRequested) {
            onAction(PostDetailViewModel.Action.Reload)
            onRefreshHandled()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.event.collect { event ->
            when (event) {
                PostDetailViewModel.Event.PostDeleted -> onDeleted()
                // 등록에 성공했을 때만 입력창을 비운다 — 실패하면 쓴 글이 남아 재시도할 수 있다
                PostDetailViewModel.Event.CommentCreated -> commentText = ""
            }
        }
    }

    Column(modifier.fillMaxSize().background(sg.paper).imePadding()) {
        SgTopBar(
            title = "게시글",
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                }
            },
            actions = {
                // 수정·삭제는 작성자 본인만 — 서버도 같은 규칙(requirePostOwner)이라 화면은 미리 감출 뿐이다
                if (uiState.isMyPost) {
                    TextButton(onClick = onEdit, enabled = !uiState.isDeletingPost) {
                        Text("수정", color = sg.accent)
                    }
                    TextButton(
                        onClick = { onAction(PostDetailViewModel.Action.DeletePost) },
                        enabled = !uiState.isDeletingPost
                    ) {
                        Text("삭제", color = sg.rust)
                    }
                }
            }
        )

        uiState.error?.let { message ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().background(sg.accentSoft).padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(message, style = SgTheme.typography.bodySmall, color = sg.ink, modifier = Modifier.weight(1f))
                TextButton(onClick = { onAction(PostDetailViewModel.Action.ClearError) }) {
                    Text("닫기", style = SgTheme.typography.labelLarge, color = sg.accent)
                }
            }
        }

        Box(Modifier.weight(1f)) {
            when {
                uiState.post == null && uiState.isLoading ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = sg.accent)
                    }

                uiState.post == null ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        TextButton(onClick = { onAction(PostDetailViewModel.Action.Reload) }) {
                            Text("다시 시도", color = sg.accent)
                        }
                    }

                // 항목 간격을 작게 잡는다 — 본문 컬럼과 댓글 행이 각자 세로 패딩을 갖고 있어
                // 여기서 12dp를 더 주면 좋아요 행과 구분선 사이가 두 배로 벌어진다.
                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item { PostBody(uiState, onAction) }
                    item {
                        Divider(color = sg.stoneBorder)
                        Text(
                            "댓글 ${uiState.comments.size}",
                            style = SgTheme.typography.labelLarge,
                            color = sg.inkSoft,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp)
                        )
                    }
                    items(uiState.topLevelComments, key = { it.id }) { comment ->
                        Column {
                            CommentRow(
                                comment = comment,
                                isMine = comment.userId == uiState.myUserId,
                                onReply = { onAction(PostDetailViewModel.Action.SetReplyTo(comment)) },
                                onDelete = { onAction(PostDetailViewModel.Action.DeleteComment(comment.id)) }
                            )
                            // 답글은 한 단계만 들여쓴다(서버가 답글의 답글을 허용하지 않는다)
                            uiState.repliesOf(comment.id).forEach { reply ->
                                CommentRow(
                                    comment = reply,
                                    isMine = reply.userId == uiState.myUserId,
                                    onReply = null,
                                    onDelete = { onAction(PostDetailViewModel.Action.DeleteComment(reply.id)) },
                                    modifier = Modifier.padding(start = 40.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        CommentComposer(
            replyTo = uiState.replyTo,
            text = commentText,
            isSubmitting = uiState.isSubmittingComment,
            onTextChange = { commentText = it },
            onCancelReply = { onAction(PostDetailViewModel.Action.SetReplyTo(null)) },
            onSubmit = { onAction(PostDetailViewModel.Action.SubmitComment(commentText)) }
        )
    }
}

@Composable
private fun PostBody(uiState: PostDetailViewModel.UiState, onAction: (PostDetailViewModel.Action) -> Unit) {
    val sg = SgTheme.colors
    val post = uiState.post ?: return

    // 아래쪽 패딩은 두지 않는다 — 마지막 줄인 좋아요 행의 IconButton이 이미 48dp 터치 영역만큼
    // 자체 여백을 갖고 있어서, 여기에 16dp를 더하면 구분선까지가 허전하게 벌어진다.
    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SgAvatar(post.authorName, size = 40.dp, imageUrl = post.authorProfileImg)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(post.authorName, style = SgTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = sg.ink)
                Text(formatRelativeTime(post.createdAt), style = SgTheme.typography.bodySmall, color = sg.inkFaint)
            }
        }
        if (post.text.isNotBlank()) {
            Text(post.text, style = SgTheme.typography.bodyMedium, color = sg.ink)
        }
        post.imageUrls.forEach { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            )
        }
        // IconButton은 48dp 터치 영역 안에 24dp 아이콘을 가운데 두므로 좌우로 12dp가 남는다 —
        // 그만큼 행을 당겨 하트가 위 작성자 이름·본문과 같은 세로선에서 시작하게 한다
        // (터치 영역은 그대로 48dp를 유지한다).
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.offset(x = (-12).dp)) {
            IconButton(
                onClick = { onAction(PostDetailViewModel.Action.ToggleLike) },
                enabled = !uiState.isTogglingLike
            ) {
                Icon(
                    if (uiState.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (uiState.isLiked) "좋아요 취소" else "좋아요",
                    tint = if (uiState.isLiked) sg.accent else sg.inkFaint
                )
            }
            Text("좋아요 ${uiState.likeCount}", style = SgTheme.typography.bodySmall, color = sg.inkSoft)
            Spacer(Modifier.width(16.dp))
            Text("댓글 ${uiState.comments.size}", style = SgTheme.typography.bodySmall, color = sg.inkSoft)
        }
    }
}

@Composable
private fun CommentRow(
    comment: Comment,
    isMine: Boolean,
    onReply: (() -> Unit)?,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors

    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        SgAvatar(comment.authorName, size = 28.dp, imageUrl = comment.authorProfileImg)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(comment.authorName, style = SgTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = sg.ink)
                Spacer(Modifier.width(6.dp))
                Text(formatRelativeTime(comment.createdAt), style = SgTheme.typography.labelSmall, color = sg.inkFaint)
            }
            Text(comment.text, style = SgTheme.typography.bodySmall, color = sg.ink)
            Row {
                if (onReply != null) {
                    TextButton(onClick = onReply, contentPadding = PaddingValues(0.dp)) {
                        Text("답글", style = SgTheme.typography.labelSmall, color = sg.inkFaint)
                    }
                }
                if (isMine) {
                    if (onReply != null) Spacer(Modifier.width(12.dp))
                    TextButton(onClick = onDelete, contentPadding = PaddingValues(0.dp)) {
                        Text("삭제", style = SgTheme.typography.labelSmall, color = sg.inkFaint)
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentComposer(
    replyTo: Comment?,
    text: String,
    isSubmitting: Boolean,
    onTextChange: (String) -> Unit,
    onCancelReply: () -> Unit,
    onSubmit: () -> Unit
) {
    val sg = SgTheme.colors

    Column(Modifier.fillMaxWidth().background(sg.paper)) {
        Divider(color = sg.stoneBorder)
        // 답글 대상이 정해지면 누구에게 다는지 보여주고, 그 자리에서 취소할 수 있게 한다
        replyTo?.let { target ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().background(sg.accentSoft).padding(start = 16.dp, end = 4.dp)
            ) {
                Text(
                    "${target.authorName}님에게 답글",
                    style = SgTheme.typography.labelSmall,
                    color = sg.ink,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onCancelReply) {
                    Icon(Icons.Default.Close, contentDescription = "답글 취소", tint = sg.inkFaint)
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // 라벨 없이 입력창만 — SgTextField의 label은 필드 위에 별도 줄로 그려져서
            // 한 줄짜리 댓글 입력에는 군더더기다(답글 대상은 위 칩이 이미 알려준다).
            SgTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onSubmit, enabled = !isSubmitting && text.isNotBlank()) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "등록",
                    tint = if (isSubmitting || text.isBlank()) sg.inkFaint else sg.accent
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
