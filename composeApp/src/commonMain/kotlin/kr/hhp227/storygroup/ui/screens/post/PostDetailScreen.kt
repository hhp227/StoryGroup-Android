package kr.hhp227.storygroup.ui.screens.post

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kr.hhp227.storygroup.di.LocalAppContainer
import kr.hhp227.storygroup.shared.domain.model.Comment
import kr.hhp227.storygroup.ui.components.SgAvatar
import kr.hhp227.storygroup.ui.components.SgCard
import kr.hhp227.storygroup.ui.components.SgPrimaryButton
import kr.hhp227.storygroup.ui.components.SgTextField
import kr.hhp227.storygroup.ui.components.SgTopBar
import kr.hhp227.storygroup.ui.components.SgVideoAttachment
import kr.hhp227.storygroup.ui.navigation.NavResult
import kr.hhp227.storygroup.ui.navigation.NavigationAction
import kr.hhp227.storygroup.ui.navigation.sessionNavigationViewModel
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
            reportPostUseCase = container.reportPostUseCase,
            reportUserUseCase = container.reportUserUseCase,
            blockUserUseCase = container.blockUserUseCase,
            getCurrentUserIdUseCase = container.getCurrentUserIdUseCase
        )
    }
}

/**
 * 게시글 상세 — 본문·이미지·좋아요·댓글(답글 포함). 웹 /groups/{id}/posts/{postId} 미러.
 * NavHost 풀스크린 목적지라 상단바는 화면이 소유한다. 삭제·차단 성공은 화면을 닫기만 하고,
 * 목록 정리는 피드 VM이 삭제·차단 알림을 받아 스냅샷에서 처리한다(전체 재조회를 피한다).
 * iosApp PostDetailView.swift와 1:1 미러
 */
@Composable
fun PostDetailScreen(
    groupId: Long,
    postId: Long,
    modifier: Modifier = Modifier,
    onNavigationAction: (NavigationAction) -> Unit = sessionNavigationViewModel()::onAction,
    pendingResults: Set<NavResult> = sessionNavigationViewModel().uiState.collectAsState().value.pendingResults,
    viewModel: PostDetailViewModel = postDetailViewModel(groupId, postId)
) {
    val uiState by viewModel.uiState.collectAsState()
    val onAction = viewModel::onAction
    val sg = SgTheme.colors
    val onBack = { onNavigationAction(NavigationAction.NavigateBack) }
    // 본문·댓글 작성자 탭 → 공개 프로필(웹 작성자 메뉴의 "프로필 보기"만 직행으로 미러 —
    // 신고·차단은 기존 더보기 메뉴, DM은 프로필 화면 버튼이 담당해 중복이 없다)
    val onOpenUserProfile = { userId: Long -> onNavigationAction(NavigationAction.NavigateToUserProfile(userId)) }
    // 수정 화면에서 돌아왔다는 신호 — 본문이 바뀌었으니 다시 읽는다(그룹 상세와 같은 규약)
    val refreshRequested = NavResult.PostUpdated(groupId, postId) in pendingResults
    val onRefreshHandled = {
        onNavigationAction(NavigationAction.ConsumeResult(NavResult.PostUpdated(groupId, postId)))
    }
    var commentText by rememberSaveable { mutableStateOf("") }
    // 상단바 더보기 메뉴 — 열린 채로 화면을 벗어나면 닫히는 게 맞아 remember면 충분하다
    var menuExpanded by remember { mutableStateOf(false) }
    // 되돌릴 수 없는 액션은 확인을 받는다(웹 confirm 미러)
    var confirmAction by remember { mutableStateOf<ConfirmAction?>(null) }
    // 지금 재생 중인 동영상 URL — 한 게시글에 동영상이 여럿이어도 재생기는 하나만 뜬다.
    // 순수 뷰 상태라 UiState가 아니라 화면이 들고 있는다(menuExpanded와 같은 성격).
    var playingVideoUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refreshRequested) {
        if (refreshRequested) {
            onAction(PostDetailViewModel.Action.Reload)
            onRefreshHandled()
            // 목록은 갱신 신호를 받지 않는다 — 수정 알림(ObservePostUpdatesUseCase)을 받은 목록 VM이
            // 자기 스냅샷에서 그 항목만 갈아끼운다(refresh를 태우면 첫 페이지부터 전체 재조회가 된다)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.event.collect { event ->
            when (event) {
                // 삭제·차단 모두 화면만 닫는다 — 목록에서 그 글을 걷어내는 일은 피드 VM이
                // 삭제·차단 알림을 받아 스냅샷에서 처리한다(전체 재조회를 피한다)
                PostDetailViewModel.Event.PostDeleted -> onBack()
                PostDetailViewModel.Event.AuthorBlocked -> onBack()
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
                // 더보기(⋮)는 항상 노출하고 메뉴 내용만 갈린다 — 내 글이면 수정·삭제,
                // 남의 글이면 신고·차단(웹 게시글 상세+UserActionMenu 미러). 권한은 서버가 판정한다.
                // 메뉴가 이 아이콘 바로 아래에 뜨도록 Box로 묶어 앵커를 잡는다
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        enabled = !uiState.isDeletingPost && !uiState.isBlocking
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "더보기")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        if (uiState.isMyPost) {
                            DropdownMenuItem(
                                onClick = {
                                    menuExpanded = false
                                    onNavigationAction(NavigationAction.NavigateToCreatePost(groupId, postId))
                                }
                            ) {
                                Text("수정", style = SgTheme.typography.bodyMedium, color = sg.ink)
                            }
                            DropdownMenuItem(
                                onClick = {
                                    menuExpanded = false
                                    onAction(PostDetailViewModel.Action.DeletePost)
                                }
                            ) {
                                Text("삭제", style = SgTheme.typography.bodyMedium, color = sg.rust)
                            }
                        } else {
                            DropdownMenuItem(
                                onClick = {
                                    menuExpanded = false
                                    confirmAction = ConfirmAction.ReportPost
                                }
                            ) {
                                Text("신고하기", style = SgTheme.typography.bodyMedium, color = sg.rust)
                            }
                            DropdownMenuItem(
                                // 글이 아직 안 실렸으면 작성자를 모르므로 차단할 수 없다
                                enabled = uiState.post != null,
                                onClick = {
                                    menuExpanded = false
                                    confirmAction = ConfirmAction.BlockAuthor
                                }
                            ) {
                                Text("차단하기", style = SgTheme.typography.bodyMedium, color = sg.rust)
                            }
                        }
                    }
                }
            }
        )

        uiState.notice?.let { message ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().background(sg.linen).padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(message, style = SgTheme.typography.bodySmall, color = sg.moss, modifier = Modifier.weight(1f))
                TextButton(onClick = { onAction(PostDetailViewModel.Action.ClearNotice) }) {
                    Text("닫기", style = SgTheme.typography.labelLarge, color = sg.accent)
                }
            }
        }

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
                    item {
                        PostBody(
                            uiState = uiState,
                            onAction = onAction,
                            onOpenUserProfile = onOpenUserProfile,
                            playingVideoUrl = playingVideoUrl,
                            onPlayVideo = { playingVideoUrl = it }
                        )
                    }
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
                                onDelete = { onAction(PostDetailViewModel.Action.DeleteComment(comment.id)) },
                                onReport = {
                                    confirmAction = ConfirmAction.ReportComment(comment.userId, comment.authorName)
                                },
                                onBlock = {
                                    confirmAction = ConfirmAction.BlockComment(comment.userId, comment.authorName)
                                },
                                onOpenAuthor = { onOpenUserProfile(comment.userId) }
                            )
                            // 답글은 한 단계만 들여쓴다(서버가 답글의 답글을 허용하지 않는다)
                            uiState.repliesOf(comment.id).forEach { reply ->
                                CommentRow(
                                    comment = reply,
                                    isMine = reply.userId == uiState.myUserId,
                                    onReply = null,
                                    onDelete = { onAction(PostDetailViewModel.Action.DeleteComment(reply.id)) },
                                    onReport = {
                                        confirmAction = ConfirmAction.ReportComment(reply.userId, reply.authorName)
                                    },
                                    onBlock = {
                                        confirmAction = ConfirmAction.BlockComment(reply.userId, reply.authorName)
                                    },
                                    onOpenAuthor = { onOpenUserProfile(reply.userId) },
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

    confirmAction?.let { action ->
        // 차단 문구는 어디서 눌렀든 같다 — 차단은 사용자 단위라 글·댓글이 함께 숨겨진다
        val blockMessage = { name: String ->
            "${name}님을 차단할까요?\n차단하면 이 사용자의 글·댓글이 내 화면에서 숨겨지고 DM이 막힙니다."
        }

        ActionConfirmDialog(
            title = when (action) {
                ConfirmAction.ReportPost -> "게시글 신고"
                is ConfirmAction.ReportComment -> "사용자 신고"
                else -> "사용자 차단"
            },
            message = when (action) {
                ConfirmAction.ReportPost -> "이 게시글을 신고할까요?\n접수된 신고는 그룹 관리자가 확인합니다."
                // 댓글엔 신고 API가 없어 작성자를 신고한다 — 접수처도 운영자로 달라서 문구를 구분한다
                is ConfirmAction.ReportComment ->
                    "${action.authorName}님을 신고할까요?\n접수된 신고는 운영자가 확인합니다."
                ConfirmAction.BlockAuthor -> blockMessage(uiState.post?.authorName ?: "")
                is ConfirmAction.BlockComment -> blockMessage(action.authorName)
            },
            confirmText = when (action) {
                ConfirmAction.ReportPost, is ConfirmAction.ReportComment -> "신고"
                else -> "차단"
            },
            isLoading = when (action) {
                ConfirmAction.ReportPost, is ConfirmAction.ReportComment -> uiState.isReporting
                else -> uiState.isBlocking
            },
            onDismiss = { confirmAction = null },
            onConfirm = {
                confirmAction = null
                onAction(
                    when (action) {
                        ConfirmAction.ReportPost -> PostDetailViewModel.Action.ReportPost
                        ConfirmAction.BlockAuthor -> PostDetailViewModel.Action.BlockAuthor
                        is ConfirmAction.ReportComment ->
                            PostDetailViewModel.Action.ReportCommentAuthor(action.userId)
                        is ConfirmAction.BlockComment ->
                            PostDetailViewModel.Action.BlockCommentAuthor(action.userId)
                    }
                )
            }
        )
    }
}

/** 더보기 메뉴의 되돌릴 수 없는 액션 — 확인 다이얼로그를 한 번 거친다 */
private sealed interface ConfirmAction {
    data object ReportPost : ConfirmAction
    data object BlockAuthor : ConfirmAction

    /** 댓글 신고 API는 없어 작성자를 신고한다 — 문구에 쓰려고 이름을 함께 싣는다 */
    data class ReportComment(val userId: Long, val authorName: String) : ConfirmAction
    data class BlockComment(val userId: Long, val authorName: String) : ConfirmAction
}

/**
 * 신고·차단 확인 다이얼로그 — GroupDetailScreen의 DmConfirmDialog와 같은 카드형.
 * 실패 메시지는 다이얼로그가 아니라 화면 상단 에러 배너에 뜬다(닫고 나서 결과가 오기 때문).
 */
@Composable
private fun ActionConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val sg = SgTheme.colors

    Dialog(onDismissRequest = onDismiss) {
        SgCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, style = SgTheme.typography.titleMedium, color = sg.ink, fontWeight = FontWeight.Bold)
                Text(message, style = SgTheme.typography.bodyMedium, color = sg.ink)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onDismiss, shape = SgTheme.shapes.button, modifier = Modifier.weight(1f)) {
                        Text("취소", color = sg.ink)
                    }
                    SgPrimaryButton(
                        text = confirmText,
                        onClick = onConfirm,
                        isLoading = isLoading,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PostBody(
    uiState: PostDetailViewModel.UiState,
    onAction: (PostDetailViewModel.Action) -> Unit,
    onOpenUserProfile: (Long) -> Unit,
    playingVideoUrl: String?,
    onPlayVideo: (String) -> Unit
) {
    val sg = SgTheme.colors
    val post = uiState.post ?: return

    // 아래쪽 패딩은 두지 않는다 — 마지막 줄인 좋아요 행의 IconButton이 이미 48dp 터치 영역만큼
    // 자체 여백을 갖고 있어서, 여기에 16dp를 더하면 구분선까지가 허전하게 벌어진다.
    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            // 작성자 영역만 탭 타깃(본문·첨부 제외) — 본인 글이면 본인 프로필(프로필 수정 버튼)로 간다
            modifier = Modifier.clickable { onOpenUserProfile(post.userId) }
        ) {
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
        // 동영상은 이미지 다음에 온다(웹 상세 페이지와 같은 순서)
        post.videoUrls.forEach { url ->
            SgVideoAttachment(
                url = url,
                isPlaying = url == playingVideoUrl,
                onPlayRequest = { onPlayVideo(url) }
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
    onReport: () -> Unit,
    onBlock: () -> Unit,
    onOpenAuthor: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors
    var menuExpanded by remember { mutableStateOf(false) }

    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        SgAvatar(
            comment.authorName,
            size = 28.dp,
            imageUrl = comment.authorProfileImg,
            modifier = Modifier.clickable(onClick = onOpenAuthor)
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    comment.authorName,
                    style = SgTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = sg.ink,
                    modifier = Modifier.clickable(onClick = onOpenAuthor)
                )
                Spacer(Modifier.width(6.dp))
                Text(formatRelativeTime(comment.createdAt), style = SgTheme.typography.labelSmall, color = sg.inkFaint)
            }
            Text(comment.text, style = SgTheme.typography.bodySmall, color = sg.ink)
            if (onReply != null) {
                TextButton(onClick = onReply, contentPadding = PaddingValues(0.dp)) {
                    Text("답글", style = SgTheme.typography.labelSmall, color = sg.inkFaint)
                }
            }
        }
        // 게시글 상단바와 같은 규칙 — 더보기는 항상 노출하고 내 댓글이면 삭제, 남의 댓글이면 신고·차단.
        // 댓글 행은 촘촘해서 기본 48dp IconButton 대신 28dp로 줄인다(채팅 입력바 버튼과 같은 처리)
        Box {
            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "더보기",
                    tint = sg.inkFaint,
                    modifier = Modifier.size(18.dp)
                )
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                if (isMine) {
                    DropdownMenuItem(
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    ) {
                        Text("삭제", style = SgTheme.typography.bodyMedium, color = sg.rust)
                    }
                } else {
                    DropdownMenuItem(
                        onClick = {
                            menuExpanded = false
                            onReport()
                        }
                    ) {
                        Text("신고하기", style = SgTheme.typography.bodyMedium, color = sg.rust)
                    }
                    DropdownMenuItem(
                        onClick = {
                            menuExpanded = false
                            onBlock()
                        }
                    ) {
                        Text("차단하기", style = SgTheme.typography.bodyMedium, color = sg.rust)
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
