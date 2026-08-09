package kr.hhp227.storygroup.ui.screens.group

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import app.cash.paging.LoadStateError
import app.cash.paging.LoadStateLoading
import app.cash.paging.compose.collectAsLazyPagingItems
import app.cash.paging.compose.itemKey
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kr.hhp227.storygroup.di.LocalAppContainer
import kr.hhp227.storygroup.shared.domain.model.GroupInvite
import kr.hhp227.storygroup.shared.domain.model.GroupJoinRequest
import kr.hhp227.storygroup.shared.domain.model.GroupMember
import kr.hhp227.storygroup.shared.domain.model.Post
import kr.hhp227.storygroup.ui.components.SgAvatar
import kr.hhp227.storygroup.ui.components.SgCard
import kr.hhp227.storygroup.ui.components.SgCollapsingHeaderScaffold
import kr.hhp227.storygroup.ui.components.SgEmptyState
import kr.hhp227.storygroup.ui.components.SgPagingFooter
import kr.hhp227.storygroup.ui.components.SgPostCard
import kr.hhp227.storygroup.ui.components.SgPrimaryButton
import kr.hhp227.storygroup.ui.components.SgTextField
import kr.hhp227.storygroup.ui.components.collapsingParallax
import kr.hhp227.storygroup.ui.theme.SgTheme
import kr.hhp227.storygroup.ui.util.formatRelativeTime

@Composable
private fun groupDetailViewModel(groupId: Long): GroupDetailViewModel {
    val container = LocalAppContainer.current

    return viewModel(key = "group-detail-$groupId") {
        GroupDetailViewModel(
            groupId = groupId,
            getGroupUseCase = container.getGroupUseCase,
            getGroupMembersUseCase = container.getGroupMembersUseCase,
            getJoinRequestsUseCase = container.getJoinRequestsUseCase,
            approveJoinRequestUseCase = container.approveJoinRequestUseCase,
            rejectJoinRequestUseCase = container.rejectJoinRequestUseCase,
            createGroupInviteUseCase = container.createGroupInviteUseCase,
            openDirectRoomUseCase = container.openDirectRoomUseCase,
            getGroupDefaultChatRoomUseCase = container.getGroupDefaultChatRoomUseCase,
            getBlockedUsersUseCase = container.getBlockedUsersUseCase,
            getCurrentUserIdUseCase = container.getCurrentUserIdUseCase,
            getGroupPostsPagingDataUseCase = container.getGroupPostsPagingDataUseCase,
            observePostUpdatesUseCase = container.observePostUpdatesUseCase,
            observeUserBlocksUseCase = container.observeUserBlocksUseCase
        )
    }
}

/**
 * 그룹 상세 — 웹 /groups/[id] 미러: 커버 배너(그라데이션 폴백+이름/설명/역할 칩)+멤버 스트립+피드.
 * 커버는 레거시 fragment_group_detail.xml처럼 콜랩싱(SgCollapsingHeaderScaffold).
 * 계층은 iosApp GroupDetailView.swift와 1:1 미러 — Screen=상태 소유(VM 선언), Content=구독+UI.
 */
@Composable
fun GroupDetailScreen(
    groupId: Long,
    onBack: () -> Unit,
    onCreatePost: () -> Unit,
    // 이 그룹의 글이라 groupId는 화면이 이미 알고 있다 — postId만 넘긴다
    onOpenPostDetail: (postId: Long) -> Unit,
    onOpenChatRoom: (chatRoomId: Long, groupId: Long?, title: String) -> Unit,
    refreshRequested: Boolean,
    onRefreshHandled: () -> Unit,
    modifier: Modifier = Modifier,
    // 라우트(백스택 엔트리) 스코프 — pop되면 함께 정리된다(ConCafe CafeScreen 패턴)
    viewModel: GroupDetailViewModel = groupDetailViewModel(groupId)
) {
    GroupDetailContent(
        viewModel = viewModel,
        onBack = onBack,
        onCreatePost = onCreatePost,
        onOpenPostDetail = onOpenPostDetail,
        onOpenChatRoom = onOpenChatRoom,
        refreshRequested = refreshRequested,
        onRefreshHandled = onRefreshHandled,
        modifier = modifier
    )
}

@Composable
private fun GroupDetailContent(
    viewModel: GroupDetailViewModel,
    onBack: () -> Unit,
    onCreatePost: () -> Unit,
    // 이 그룹의 글이라 groupId는 화면이 이미 알고 있다 — postId만 넘긴다
    onOpenPostDetail: (postId: Long) -> Unit,
    onOpenChatRoom: (chatRoomId: Long, groupId: Long?, title: String) -> Unit,
    refreshRequested: Boolean,
    onRefreshHandled: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    // 상태에서 pagingData만 뽑아낸 스트림을 수집 — Paging-CRUD 샘플·iOS($state.map)와 동일 관용구
    val pagingDataFlow = remember(viewModel) {
        viewModel.uiState.map { it.pagingData }.distinctUntilChanged()
    }
    val lazyPagingItems = pagingDataFlow.collectAsLazyPagingItems()
    val sg = SgTheme.colors
    // 그룹/멤버는 UiState, 피드는 Paging3 LoadState — 다음 페이지 트리거는 prefetchDistance가 담당
    val refreshState = lazyPagingItems.loadState.refresh
    var showInviteDialog by rememberSaveable { mutableStateOf(false) }
    // DM 확인 다이얼로그 대상 — 멤버 스트립에서 타인을 탭하면 채워진다
    var dmTargetMember by remember { mutableStateOf<GroupMember?>(null) }

    // 상세 진입 시 신선화 — VM이 탭 전환에도 유지되므로 재진입 때도 최신화된다
    LaunchedEffect(viewModel) {
        viewModel.onAction(GroupDetailViewModel.Action.Refresh)
    }
    // 작성 화면에서 돌아온 결과 — 피드를 첫 페이지부터 다시 읽는다
    LaunchedEffect(refreshRequested) {
        if (refreshRequested) {
            viewModel.onAction(GroupDetailViewModel.Action.RefreshFeed)
            onRefreshHandled()
        }
    }
    // VM의 일회성 갱신 이벤트 — 프레젠터 refresh()가 활성 PagingSource를 무효화해
    // 같은 스트림이 새 세대(첫 페이지)를 방출한다(홈 피드와 동일 패턴)
    LaunchedEffect(Unit) {
        viewModel.event.collect { event ->
            when (event) {
                GroupDetailViewModel.Event.RefreshFeed -> lazyPagingItems.refresh()
                is GroupDetailViewModel.Event.DmOpened -> {
                    dmTargetMember = null
                    // DM 방은 groupId 없이 접근한다(/api/dm 경로) — 제목은 상대 이름
                    onOpenChatRoom(event.chatRoomId, null, event.title)
                }
            }
        }
    }
    SgCollapsingHeaderScaffold(
        // 로드 전엔 빈 제목 — 커버 그라데이션(groupId 기반)은 즉시 그려진다
        title = uiState.group?.name.orEmpty(),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
        },
        // 그룹 채팅방 진입 — 상단바 액션(레거시 group.xml action_chat·웹 커버 "채팅" 버튼 미러).
        // 기본 방 id는 상세 로드에 실려 온다 — 로드 전/실패 시엔 버튼이 숨는다
        actions = {
            uiState.defaultChatRoomId?.let { chatRoomId ->
                IconButton(onClick = {
                    // 방 제목은 허브(그룹 방 목록)와 동일하게 그룹명을 쓴다
                    onOpenChatRoom(chatRoomId, viewModel.groupId, uiState.group?.name.orEmpty())
                }) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "채팅")
                }
            }
        },
        // 레거시 fragment_group_detail.xml의 fab 미러
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreatePost,
                backgroundColor = sg.accent,
                contentColor = sg.onAccent
            ) {
                Icon(Icons.Default.Add, contentDescription = "글쓰기")
            }
        },
        // 당겨서 새로고침 — 그룹 정보(멤버/가입 신청 포함)와 피드를 함께 갱신한다.
        // 스피너는 데이터가 이미 있는 갱신에만 돈다 — 첫 로드는 목록 중앙 스피너가 담당(홈과 동일)
        isRefreshing = lazyPagingItems.itemCount > 0 && refreshState is LoadStateLoading,
        onRefresh = {
            viewModel.onAction(GroupDetailViewModel.Action.Refresh)
            viewModel.onAction(GroupDetailViewModel.Action.RefreshFeed)
        },
        header = { listState ->
            // group.image 있으면 실사진, 없으면 웹 GroupCover 그라데이션 폴백 — 콘텐츠 전체가 패럴럭스로 접힌다
            val coverImage = uiState.group?.image
            Box(Modifier.matchParentSize().collapsingParallax(listState)) {
                if (coverImage != null) {
                    AsyncImage(
                        model = coverImage,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                } else {
                    Box(Modifier.matchParentSize().background(groupCoverBrush(viewModel.groupId, sg)))
                }
            }
            // 웹 커버 하단 스크림(0.05→0.62) 위 그룹명/설명/역할 칩 미러
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        0.35f to Color.Black.copy(alpha = 0.05f),
                        1f to Color.Black.copy(alpha = 0.62f)
                    )
                )
            )
            uiState.group?.let { group ->
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            group.name,
                            style = SgTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(8.dp))
                        RoleChip(group.myRole)
                    }
                    if (!group.description.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            group.description.orEmpty(),
                            style = SgTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.88f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        modifier = modifier
    ) {
        val appendState = lazyPagingItems.loadState.append

        // 모더레이터 인박스 — 웹 GroupMemberList처럼 멤버 목록 위에 노출(joinRequests는 모더레이터에게만 채워진다)
        if (uiState.joinRequests.isNotEmpty()) {
            item(key = "join-requests") {
                JoinRequestInbox(
                    requests = uiState.joinRequests,
                    processingUserId = uiState.processingRequestUserId,
                    actionError = uiState.actionError,
                    onApprove = { viewModel.onAction(GroupDetailViewModel.Action.ApproveJoinRequest(it)) },
                    onReject = { viewModel.onAction(GroupDetailViewModel.Action.RejectJoinRequest(it)) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
        // 모더레이터 전용 초대코드 만들기 — 승인 우회 가입 경로라 인박스와 같은 조정 도구로 묶는다
        if (uiState.canModerate) {
            item(key = "invite-code") {
                OutlinedButton(
                    onClick = { showInviteDialog = true },
                    shape = SgTheme.shapes.button,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Text("초대코드 만들기", style = SgTheme.typography.labelLarge, color = sg.accent)
                }
            }
        }
        if (uiState.visibleMembers.isNotEmpty()) {
            item(key = "members") {
                MemberStrip(
                    members = uiState.visibleMembers,
                    myUserId = uiState.myUserId,
                    onMemberClick = { dmTargetMember = it },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
        if (uiState.error != null) {
            item(key = "detail-error") {
                Column(
                    modifier = Modifier.fillParentMaxWidth().padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(uiState.error.orEmpty(), style = SgTheme.typography.bodyMedium, color = sg.rust)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.onAction(GroupDetailViewModel.Action.Refresh) }) {
                        Text("다시 시도", color = sg.accent)
                    }
                }
            }
        }
        when {
            lazyPagingItems.itemCount == 0 && refreshState is LoadStateLoading -> item(key = "feed-loading") {
                Box(
                    Modifier.fillParentMaxWidth().padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = sg.accent)
                }
            }
            lazyPagingItems.itemCount == 0 && refreshState is LoadStateError -> item(key = "feed-error") {
                Column(
                    modifier = Modifier.fillParentMaxWidth().padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        refreshState.error.message ?: "피드를 불러오지 못했습니다.",
                        style = SgTheme.typography.bodyMedium,
                        color = sg.rust
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = lazyPagingItems::retry) {
                        Text("다시 시도", color = sg.accent)
                    }
                }
            }
            lazyPagingItems.itemCount == 0 -> item(key = "feed-empty") {
                SgEmptyState(
                    title = "아직 이야기가 없습니다",
                    subtitle = "첫 이야기를 남겨보세요.",
                    modifier = Modifier.fillParentMaxWidth().padding(vertical = 48.dp)
                )
            }
            else -> {
                items(count = lazyPagingItems.itemCount, key = lazyPagingItems.itemKey(Post::id)) { index ->
                    lazyPagingItems[index]?.let { post ->
                        SgPostCard(post, Modifier.padding(horizontal = 16.dp)) { onOpenPostDetail(post.id) }
                    }
                }
                if (appendState is LoadStateLoading || appendState is LoadStateError) {
                    item(key = "feed-footer") {
                        SgPagingFooter(
                            isLoadingMore = appendState is LoadStateLoading,
                            error = (appendState as? LoadStateError)?.error?.message,
                            onRetry = lazyPagingItems::retry
                        )
                    }
                }
            }
        }
    }
    if (showInviteDialog) {
        InviteDialog(
            invite = uiState.createdInvite,
            isLoading = uiState.isCreatingInvite,
            error = uiState.inviteError,
            onDismiss = {
                showInviteDialog = false
                // 닫을 때 결과를 비워 다음에 열면 다시 생성 폼부터 시작한다
                viewModel.onAction(GroupDetailViewModel.Action.DismissInvite)
            },
            onCreate = { maxUses, expiresInDays ->
                viewModel.onAction(GroupDetailViewModel.Action.CreateInvite(maxUses, expiresInDays))
            }
        )
    }
    dmTargetMember?.let { member ->
        DmConfirmDialog(
            memberName = member.name,
            isLoading = uiState.isOpeningDm,
            error = uiState.dmError,
            onDismiss = {
                dmTargetMember = null
                viewModel.onAction(GroupDetailViewModel.Action.DismissDm)
            },
            onConfirm = { viewModel.onAction(GroupDetailViewModel.Action.OpenDm(member.userId, member.name)) }
        )
    }
}

/** 멤버 탭 → 1:1 DM 확인 다이얼로그 — 성공 시 DmOpened 이벤트로 채팅방으로 이동한다 */
@Composable
private fun DmConfirmDialog(
    memberName: String,
    isLoading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val sg = SgTheme.colors

    Dialog(onDismissRequest = onDismiss) {
        SgCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "1:1 DM",
                    style = SgTheme.typography.titleMedium,
                    color = sg.ink,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${memberName}님과 1:1 DM을 시작할까요?",
                    style = SgTheme.typography.bodyMedium,
                    color = sg.ink
                )
                error?.let {
                    Text(it, style = SgTheme.typography.bodySmall, color = sg.rust)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = SgTheme.shapes.button,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("취소", color = sg.ink)
                    }
                    SgPrimaryButton(
                        text = "DM 시작",
                        onClick = onConfirm,
                        isLoading = isLoading,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * 모더레이터용 초대코드 다이얼로그 — 생성 폼과 결과(코드+복사)를 한 다이얼로그에서 전환한다.
 * 웹엔 생성 UI가 없어 앱이 자체 디자인 — 계약은 백엔드 CreateInviteRequest(@Min 1, @Max 365) 미러.
 */
@Composable
private fun InviteDialog(
    invite: GroupInvite?,
    isLoading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onCreate: (maxUses: Int?, expiresInDays: Int?) -> Unit
) {
    val sg = SgTheme.colors

    Dialog(onDismissRequest = onDismiss) {
        SgCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "초대코드 만들기",
                        style = SgTheme.typography.titleMedium,
                        color = sg.ink,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "닫기", tint = sg.inkSoft)
                    }
                }
                if (invite == null) {
                    var maxUsesText by rememberSaveable { mutableStateOf("") }
                    var expiresInDaysText by rememberSaveable { mutableStateOf("") }
                    val maxUses = maxUsesText.toIntOrNull()
                    val expiresInDays = expiresInDaysText.toIntOrNull()

                    Text(
                        "코드를 전달받은 사람은 승인 없이 바로 가입됩니다.",
                        style = SgTheme.typography.bodySmall,
                        color = sg.inkSoft
                    )
                    SgTextField(
                        value = maxUsesText,
                        onValueChange = { maxUsesText = it.filter(Char::isDigit) },
                        label = "최대 사용 횟수 (비우면 무제한)",
                        keyboardType = KeyboardType.Number
                    )
                    SgTextField(
                        value = expiresInDaysText,
                        onValueChange = { expiresInDaysText = it.filter(Char::isDigit) },
                        label = "유효 기간(일, 비우면 무기한)",
                        keyboardType = KeyboardType.Number
                    )
                    error?.let {
                        Text(it, style = SgTheme.typography.bodySmall, color = sg.rust)
                    }
                    SgPrimaryButton(
                        text = "만들기",
                        onClick = { onCreate(maxUses, expiresInDays) },
                        // 서버 검증(@Min 1, @Max 365)을 입력 단계에서 막는다 — 빈칸은 무제한/무기한
                        enabled = (maxUsesText.isEmpty() || (maxUses ?: 0) >= 1) &&
                            (expiresInDaysText.isEmpty() || (expiresInDays ?: 0) in 1..365),
                        isLoading = isLoading,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    val clipboard = LocalClipboardManager.current
                    var copied by remember { mutableStateOf(false) }
                    val limitLabel = listOfNotNull(
                        invite.maxUses?.let { "최대 ${it}회 사용" },
                        // 서버 ISO-8601 원문에서 날짜만 잘라 보여준다
                        invite.expiresAt?.let { "${it.take(10)}까지 유효" }
                    ).joinToString(" · ").ifEmpty { "사용 제한 없음" }

                    Text(
                        invite.code,
                        style = SgTheme.typography.titleLarge,
                        color = sg.ink,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 4.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        limitLabel,
                        style = SgTheme.typography.bodySmall,
                        color = sg.inkSoft,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    SgPrimaryButton(
                        text = if (copied) "복사됨" else "코드 복사",
                        onClick = {
                            clipboard.setText(AnnotatedString(invite.code))
                            copied = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/** 모더레이터용 가입 신청 인박스 — 웹 GroupMemberList의 "가입 신청 N건" 섹션 미러 */
@Composable
private fun JoinRequestInbox(
    requests: List<GroupJoinRequest>,
    processingUserId: Long?,
    actionError: String?,
    onApprove: (Long) -> Unit,
    onReject: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("가입 신청 ${requests.size}건", style = SgTheme.typography.titleSmall, color = sg.ink)
        if (actionError != null) {
            Text(actionError, style = SgTheme.typography.bodySmall, color = sg.rust)
        }
        requests.forEach { request ->
            JoinRequestCard(
                request = request,
                // VM이 한 건씩만 처리하므로 처리 중엔 모든 행의 버튼을 잠근다
                enabled = processingUserId == null,
                isProcessing = processingUserId == request.userId,
                onApprove = { onApprove(request.userId) },
                onReject = { onReject(request.userId) }
            )
        }
    }
}

@Composable
private fun JoinRequestCard(
    request: GroupJoinRequest,
    enabled: Boolean,
    isProcessing: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors

    SgCard(modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SgAvatar(request.name, imageUrl = request.profileImg)
            Column(Modifier.weight(1f)) {
                Text(
                    request.name,
                    style = SgTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = sg.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${formatRelativeTime(request.requestedAt)} 신청",
                    style = SgTheme.typography.labelSmall,
                    color = sg.inkFaint
                )
            }
            Button(
                onClick = onApprove,
                enabled = enabled,
                shape = SgTheme.shapes.button,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = sg.accent,
                    contentColor = sg.onAccent,
                    disabledBackgroundColor = sg.accentSoft,
                    disabledContentColor = sg.inkFaint
                )
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(14.dp).height(14.dp),
                        strokeWidth = 2.dp,
                        color = sg.inkFaint
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text("승인", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = onReject, enabled = enabled, shape = SgTheme.shapes.button) {
                Text("거절", color = if (enabled) sg.ink else sg.inkFaint)
            }
        }
    }
}

/** 웹 사이드바 MemberPanel의 앱 변형 — 수평 아바타 스트립. 타인을 탭하면 1:1 DM 확인으로 이어진다 */
@Composable
private fun MemberStrip(
    members: List<GroupMember>,
    myUserId: Long?,
    onMemberClick: (GroupMember) -> Unit,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors

    Column(modifier) {
        Text("멤버 ${members.size}", style = SgTheme.typography.titleSmall, color = sg.ink)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(members, key = GroupMember::userId) { member ->
                // 본인은 DM 대상이 아니라 탭도 막는다(서버도 self-DM은 400)
                val isSelf = member.userId == myUserId

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = if (isSelf) Modifier else Modifier.clickable { onMemberClick(member) }
                ) {
                    SgAvatar(member.name, imageUrl = member.profileImg)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        member.name,
                        style = SgTheme.typography.labelSmall,
                        color = sg.inkSoft,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
