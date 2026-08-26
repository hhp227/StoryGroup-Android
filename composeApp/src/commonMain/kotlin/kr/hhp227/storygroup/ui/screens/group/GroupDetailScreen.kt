package kr.hhp227.storygroup.ui.screens.group

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.AlertDialog
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
import app.cash.paging.LoadStateError
import app.cash.paging.LoadStateLoading
import app.cash.paging.compose.LazyPagingItems
import app.cash.paging.compose.collectAsLazyPagingItems
import app.cash.paging.compose.itemKey
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kr.hhp227.storygroup.di.screenViewModel
import kr.hhp227.storygroup.di.sessionViewModel
import kr.hhp227.storygroup.shared.domain.model.GroupInvite
import kr.hhp227.storygroup.shared.domain.model.GroupJoinRequest
import kr.hhp227.storygroup.shared.domain.model.GroupMember
import kr.hhp227.storygroup.shared.domain.model.Post
import kr.hhp227.storygroup.ui.components.CollapsingTabRowHeight
import kr.hhp227.storygroup.ui.components.SgAvatar
import kr.hhp227.storygroup.ui.components.SgCard
import kr.hhp227.storygroup.ui.components.SgCollapsingTabScaffold
import kr.hhp227.storygroup.ui.components.SgEmptyState
import kr.hhp227.storygroup.ui.components.SgPagingFooter
import kr.hhp227.storygroup.ui.components.SgPostCard
import kr.hhp227.storygroup.ui.components.SgPrimaryButton
import kr.hhp227.storygroup.ui.components.SgTextField
import kr.hhp227.storygroup.ui.navigation.NavResult
import kr.hhp227.storygroup.ui.navigation.NavigationAction
import kr.hhp227.storygroup.ui.navigation.sessionNavigationViewModel
import kr.hhp227.storygroup.ui.screens.profile.ProfileViewModel
import kr.hhp227.storygroup.ui.theme.SgTheme
import kr.hhp227.storygroup.ui.util.formatRelativeTime
import kr.hhp227.storygroup.ui.util.postShareText
import kr.hhp227.storygroup.ui.util.rememberShareLauncher

/**
 * 그룹 상세 — 웹 /groups/[id] 미러: 커버 배너(그라데이션 폴백+이름/설명/역할 칩)+5탭
 * (소식/앨범/일정/멤버/설정). 커버는 레거시 fragment_group_detail.xml처럼 콜랩싱
 * (SgCollapsingTabScaffold, 탭바는 레거시 TabLayout+ViewPager2 미러).
 * 계층은 iosApp GroupDetailView.swift와 1:1 미러 — Screen=상태 소유(VM 선언), Content=구독+UI.
 */
@Composable
fun GroupDetailScreen(
    groupId: Long,
    modifier: Modifier = Modifier,
    onNavigationAction: (NavigationAction) -> Unit = sessionNavigationViewModel()::onAction,
    pendingResults: Set<NavResult> = sessionNavigationViewModel().uiState.collectAsState().value.pendingResults,
    // 라우트(백스택 엔트리) 스코프 — pop되면 함께 정리된다(ConCafe CafeScreen 패턴).
    // 탭 상태는 레거시(탭 Fragment마다 VM)처럼 탭별 VM이 각자 소유한다
    viewModel: GroupDetailViewModel = screenViewModel(key = "group-detail-$groupId") {
        GroupDetailViewModel(
            groupId = groupId,
            getGroupUseCase = it.getGroupUseCase,
            getGroupDefaultChatRoomUseCase = it.getGroupDefaultChatRoomUseCase
        )
    },
    feedViewModel: GroupFeedViewModel = screenViewModel(key = "group-feed-$groupId") {
        GroupFeedViewModel(
            groupId = groupId,
            getGroupPostsPagingDataUseCase = it.getGroupPostsPagingDataUseCase,
            observePostUpdatesUseCase = it.observePostUpdatesUseCase,
            observeUserBlocksUseCase = it.observeUserBlocksUseCase,
            observePostDeletionsUseCase = it.observePostDeletionsUseCase,
            togglePostLikeUseCase = it.togglePostLikeUseCase
        )
    },
    albumViewModel: GroupAlbumViewModel = screenViewModel(key = "group-album-$groupId") {
        GroupAlbumViewModel(
            groupId = groupId,
            getGroupPhotosPagingDataUseCase = it.getGroupPhotosPagingDataUseCase
        )
    },
    membersViewModel: GroupMembersViewModel = screenViewModel(key = "group-members-$groupId") {
        GroupMembersViewModel(
            groupId = groupId,
            getGroupMembersUseCase = it.getGroupMembersUseCase,
            getJoinRequestsUseCase = it.getJoinRequestsUseCase,
            approveJoinRequestUseCase = it.approveJoinRequestUseCase,
            rejectJoinRequestUseCase = it.rejectJoinRequestUseCase,
            createGroupInviteUseCase = it.createGroupInviteUseCase,
            getBlockedUsersUseCase = it.getBlockedUsersUseCase,
            getCurrentUserIdUseCase = it.getCurrentUserIdUseCase
        )
    },
    eventsViewModel: GroupEventsViewModel = screenViewModel(key = "group-events-$groupId") {
        GroupEventsViewModel(
            groupId = groupId,
            getGroupEventsUseCase = it.getGroupEventsUseCase,
            getEventDetailUseCase = it.getEventDetailUseCase,
            createEventUseCase = it.createEventUseCase,
            deleteEventUseCase = it.deleteEventUseCase,
            rsvpEventUseCase = it.rsvpEventUseCase,
            cancelEventRsvpUseCase = it.cancelEventRsvpUseCase,
            getCurrentUserIdUseCase = it.getCurrentUserIdUseCase
        )
    },
    settingsViewModel: GroupSettingsViewModel = screenViewModel(key = "group-settings-$groupId") {
        GroupSettingsViewModel(
            groupId = groupId,
            getGroupUseCase = it.getGroupUseCase,
            deleteGroupUseCase = it.deleteGroupUseCase,
            leaveGroupUseCase = it.leaveGroupUseCase
        )
    },
    // 유저 설정 행(설정 탭) — 프로필 탭/드로어 헤더와 같은 세션 스코프 인스턴스
    profileViewModel: ProfileViewModel = sessionViewModel { ProfileViewModel(it.getMyProfileUseCase) }
) {
    GroupDetailContent(
        viewModel = viewModel,
        feedViewModel = feedViewModel,
        albumViewModel = albumViewModel,
        membersViewModel = membersViewModel,
        eventsViewModel = eventsViewModel,
        settingsViewModel = settingsViewModel,
        profileViewModel = profileViewModel,
        onBack = { onNavigationAction(NavigationAction.NavigateBack) },
        onCreatePost = { onNavigationAction(NavigationAction.NavigateToCreatePost(groupId)) },
        // 이 그룹의 글이라 groupId는 화면이 이미 알고 있다 — postId만 넘어온다
        onOpenPostDetail = { postId -> onNavigationAction(NavigationAction.NavigateToPostDetail(groupId, postId)) },
        onOpenChatRoom = { chatRoomId, gid, title ->
            onNavigationAction(NavigationAction.NavigateToChatRoom(chatRoomId, gid, title))
        },
        // 작성 화면이 남긴 결과 — 그룹 피드는 화면이 lazyPagingItems.refresh()로 갱신
        refreshRequested = NavResult.PostCreated(groupId) in pendingResults,
        onRefreshHandled = { onNavigationAction(NavigationAction.ConsumeResult(NavResult.PostCreated(groupId))) },
        // 설정 탭에서 삭제/나가기 성공 — 화면이 스스로 닫히고(pop) 그룹 목록을 갱신해야 한다
        onGroupClosed = {
            onNavigationAction(NavigationAction.PublishResult(NavResult.GroupsChanged))
            onNavigationAction(NavigationAction.NavigateBack)
        },
        // 그룹 정보 수정 화면에서 돌아온 결과 — 상세·설정 탭을 다시 읽는다
        groupUpdateRequested = NavResult.GroupUpdated(groupId) in pendingResults,
        onGroupUpdateHandled = {
            onNavigationAction(NavigationAction.ConsumeResult(NavResult.GroupUpdated(groupId)))
        },
        // 설정 탭 메뉴의 풀스크린 진입 4종 — 이제 화면이 직접 라우팅 액션을 낸다
        onOpenGroupEdit = { onNavigationAction(NavigationAction.NavigateToGroupEdit(groupId)) },
        onOpenAccountSettings = { onNavigationAction(NavigationAction.NavigateToAccountSettings) },
        onOpenAppSettings = { onNavigationAction(NavigationAction.NavigateToAppSettings) },
        onOpenGroupReports = { onNavigationAction(NavigationAction.NavigateToGroupReports(groupId)) },
        // 멤버 탭 셀 탭 → 공개 프로필 다이얼로그(DM은 프로필의 버튼 몫)
        onOpenUserProfile = { userId -> onNavigationAction(NavigationAction.NavigateToUserProfile(userId)) },
        modifier = modifier
    )
}

@Composable
private fun GroupDetailContent(
    viewModel: GroupDetailViewModel,
    feedViewModel: GroupFeedViewModel,
    albumViewModel: GroupAlbumViewModel,
    membersViewModel: GroupMembersViewModel,
    eventsViewModel: GroupEventsViewModel,
    settingsViewModel: GroupSettingsViewModel,
    profileViewModel: ProfileViewModel,
    onBack: () -> Unit,
    onCreatePost: () -> Unit,
    // 이 그룹의 글이라 groupId는 화면이 이미 알고 있다 — postId만 넘긴다
    onOpenPostDetail: (postId: Long) -> Unit,
    onOpenChatRoom: (chatRoomId: Long, groupId: Long?, title: String) -> Unit,
    refreshRequested: Boolean,
    onRefreshHandled: () -> Unit,
    onGroupClosed: () -> Unit,
    groupUpdateRequested: Boolean,
    onGroupUpdateHandled: () -> Unit,
    onOpenGroupEdit: () -> Unit,
    onOpenAccountSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenGroupReports: () -> Unit,
    onOpenUserProfile: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val profileUiState by profileViewModel.uiState.collectAsState()
    val feedUiState by feedViewModel.uiState.collectAsState()
    val membersUiState by membersViewModel.uiState.collectAsState()
    val eventsUiState by eventsViewModel.uiState.collectAsState()
    val settingsUiState by settingsViewModel.uiState.collectAsState()
    // 상태에서 pagingData만 뽑아낸 스트림을 수집 — Paging-CRUD 샘플·iOS($state.map)와 동일 관용구.
    // 프레젠터는 페이저 밖(Content 수준)에서 수집한다 — 페이지 dispose에 스크롤·스냅샷을 잃지 않게
    val pagingDataFlow = remember(feedViewModel) {
        feedViewModel.uiState.map { it.pagingData }.distinctUntilChanged()
    }
    val lazyPagingItems = pagingDataFlow.collectAsLazyPagingItems()
    // 앨범 탭 — 피드와 동일 관용구, 상태에서 photosPagingData만 뽑아낸 스트림을 수집
    val photosPagingDataFlow = remember(albumViewModel) {
        albumViewModel.uiState.map { it.photosPagingData }.distinctUntilChanged()
    }
    val photoLazyPagingItems = photosPagingDataFlow.collectAsLazyPagingItems()
    val sg = SgTheme.colors
    // 레거시 R.array.tab_name(소식/앨범/맴버/설정)에 일정 추가 — 일정·설정은 빈 화면(추후 구현)
    val tabs = remember { listOf("소식", "앨범", "일정", "멤버", "설정") }
    val pagerState = rememberPagerState { tabs.size }
    var showInviteDialog by rememberSaveable { mutableStateOf(false) }
    // 컴포지션에서 한 번만 선언해 카드마다 재사용한다
    val share = rememberShareLauncher()

    // 상세 진입 시 신선화 — VM들이 탭 전환에도 유지되므로 재진입 때도 최신화된다
    LaunchedEffect(viewModel) {
        viewModel.onAction(GroupDetailViewModel.Action.Refresh)
        membersViewModel.onAction(GroupMembersViewModel.Action.Refresh)
    }
    // 작성 화면에서 돌아온 결과 — 피드·앨범을 첫 페이지부터 다시 읽는다(프레젠터 직접 refresh)
    LaunchedEffect(refreshRequested) {
        if (refreshRequested) {
            lazyPagingItems.refresh()
            photoLazyPagingItems.refresh()
            onRefreshHandled()
        }
    }
    // 그룹 정보 수정에서 돌아온 결과 — 커버·제목과 설정 탭 판정 그룹을 다시 읽는다
    LaunchedEffect(groupUpdateRequested) {
        if (groupUpdateRequested) {
            viewModel.onAction(GroupDetailViewModel.Action.Refresh)
            settingsViewModel.onAction(GroupSettingsViewModel.Action.Refresh)
            onGroupUpdateHandled()
        }
    }
    // 설정 탭 일회성 이벤트 — 삭제/나가기 성공 시 화면 닫기(저장 갱신은 위 groupUpdateRequested/
    // pendingResults의 NavResult.GroupUpdated 소비 경로)
    LaunchedEffect(settingsViewModel) {
        settingsViewModel.event.collect { event ->
            when (event) {
                GroupSettingsViewModel.Event.Closed -> onGroupClosed()
            }
        }
    }
    SgCollapsingTabScaffold(
        // 로드 전엔 빈 제목 — 커버 그라데이션(groupId 기반)은 즉시 그려진다
        title = uiState.group?.name.orEmpty(),
        tabs = tabs,
        pagerState = pagerState,
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
        // 레거시 isTabPositionZero 미러 — 글쓰기 FAB는 소식 탭에서만
        floatingActionButton = if (pagerState.currentPage == 0) {
            {
                // 레거시 fragment_group_detail.xml의 fab 미러
                FloatingActionButton(
                    onClick = onCreatePost,
                    backgroundColor = sg.accent,
                    contentColor = sg.onAccent
                ) {
                    Icon(Icons.Default.Add, contentDescription = "글쓰기")
                }
            }
        } else null,
        // 스피너는 데이터가 이미 있는 갱신에만(첫 로드는 각 탭의 중앙 스피너 담당 — 기존 규칙 유지)
        isRefreshing = uiState.isLoading && uiState.group != null,
        // 현재 탭 무관하게 상세+멤버+피드+앨범 함께 갱신 — iOS .refreshable과 대칭(단순 우선)
        onRefresh = {
            viewModel.onAction(GroupDetailViewModel.Action.Refresh)
            membersViewModel.onAction(GroupMembersViewModel.Action.Refresh)
            lazyPagingItems.refresh()
            photoLazyPagingItems.refresh()
            eventsViewModel.onAction(GroupEventsViewModel.Action.Refresh)
        },
        header = { _ ->
            // group.image 있으면 실사진, 없으면 웹 GroupCover 그라데이션 폴백
            val coverImage = uiState.group?.image
            Box(Modifier.matchParentSize()) {
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
                        // 커버가 탭바 영역까지 커졌다 — 텍스트는 투명 탭바 위에서 끝나야 안 가린다
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp + CollapsingTabRowHeight)
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
    ) { page ->
        when (page) {
            0 -> GroupFeedTab(
                detailError = uiState.error,
                lazyPagingItems = lazyPagingItems,
                onRetryDetail = { viewModel.onAction(GroupDetailViewModel.Action.Refresh) },
                onToggleLike = { feedViewModel.onAction(GroupFeedViewModel.Action.ToggleLike(it)) },
                onShare = { share(postShareText(it)) },
                onOpenPostDetail = onOpenPostDetail
            )
            1 -> GroupAlbumTab(
                lazyPagingItems = photoLazyPagingItems,
                onOpenPostDetail = onOpenPostDetail
            )
            2 -> GroupEventsTab(
                uiState = eventsUiState,
                canModerate = uiState.canModerate,
                onAction = eventsViewModel::onAction
            )
            3 -> GroupMembersTab(
                uiState = membersUiState,
                canModerate = uiState.canModerate,
                onRetry = { membersViewModel.onAction(GroupMembersViewModel.Action.Refresh) },
                onApprove = { membersViewModel.onAction(GroupMembersViewModel.Action.ApproveJoinRequest(it)) },
                onReject = { membersViewModel.onAction(GroupMembersViewModel.Action.RejectJoinRequest(it)) },
                onShowInvite = { showInviteDialog = true },
                // 멤버 탭=프로필 다이얼로그 진입 — DM은 프로필의 버튼 몫(검색·친구 탭과 동일 규칙)
                onMemberClick = { onOpenUserProfile(it.userId) }
            )
            else -> GroupSettingsTab(
                uiState = settingsUiState,
                profile = profileUiState.profile,
                onAction = settingsViewModel::onAction,
                onOpenGroupEdit = onOpenGroupEdit,
                onOpenAccountSettings = onOpenAccountSettings,
                onOpenAppSettings = onOpenAppSettings,
                onOpenGroupReports = onOpenGroupReports
            )
        }
    }
    if (showInviteDialog) {
        InviteDialog(
            invite = membersUiState.createdInvite,
            isLoading = membersUiState.isCreatingInvite,
            error = membersUiState.inviteError,
            onDismiss = {
                showInviteDialog = false
                // 닫을 때 결과를 비워 다음에 열면 다시 생성 폼부터 시작한다
                membersViewModel.onAction(GroupMembersViewModel.Action.DismissInvite)
            },
            onCreate = { maxUses, expiresInDays ->
                membersViewModel.onAction(GroupMembersViewModel.Action.CreateInvite(maxUses, expiresInDays))
            }
        )
    }
    feedUiState.likeError?.let { message ->
        AlertDialog(
            onDismissRequest = { feedViewModel.onAction(GroupFeedViewModel.Action.DismissLikeError) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { feedViewModel.onAction(GroupFeedViewModel.Action.DismissLikeError) }) {
                    Text("확인", color = SgTheme.colors.accent)
                }
            }
        )
    }
}

/** 소식 탭 — 기존 피드 목록 그대로(인박스·멤버 섹션은 멤버 탭으로 이동). iosApp feedTab 미러 */
@Composable
private fun GroupFeedTab(
    // 상세(커버) 로드 실패 문구 — 피드 목록 위에 재시도와 함께 그린다(기존 배치 유지)
    detailError: String?,
    lazyPagingItems: LazyPagingItems<Post>,
    onRetryDetail: () -> Unit,
    onToggleLike: (Post) -> Unit,
    onShare: (Post) -> Unit,
    onOpenPostDetail: (postId: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors
    val refreshState = lazyPagingItems.loadState.refresh
    val appendState = lazyPagingItems.loadState.append

    // 첫 로드·복귀 재표출 중엔 리스트를 컴포즈하지 않는다 — 빈 리스트로 한 프레임이라도
    // 측정되면 복원된 LazyListState 인덱스가 0으로 클램프돼, 채팅방·게시글 상세 등을
    // 다녀올 때 스크롤 위치가 사라진다(캐시 표출 전 itemCount=0 프레임이 원인)
    if (lazyPagingItems.itemCount == 0 && refreshState is LoadStateLoading) {
        Box(
            modifier.fillMaxSize().padding(top = 48.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            CircularProgressIndicator(color = sg.accent)
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (detailError != null) {
            item(key = "detail-error") {
                Column(
                    modifier = Modifier.fillParentMaxWidth().padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(detailError, style = SgTheme.typography.bodyMedium, color = sg.rust)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onRetryDetail) {
                        Text("다시 시도", color = sg.accent)
                    }
                }
            }
        }
        when {
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
                        SgPostCard(
                            post,
                            Modifier.padding(horizontal = 16.dp),
                            onToggleLike = { onToggleLike(post) },
                            onShare = { onShare(post) }
                        ) { onOpenPostDetail(post.id) }
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
}

/**
 * 멤버 탭 — 가입 신청 인박스+초대코드(모더레이터, 멤버 관리 성격이라 여기 모음) 위에
 * 4열 멤버 그리드(레거시 MemberFragment 미러 — 기존 수평 MemberStrip 대체).
 * 멤버를 탭하면 공개 프로필로 이어진다(DM은 프로필의 버튼 몫). iosApp membersTab 미러.
 */
@Composable
private fun GroupMembersTab(
    uiState: GroupMembersViewModel.UiState,
    canModerate: Boolean,
    onRetry: () -> Unit,
    onApprove: (Long) -> Unit,
    onReject: (Long) -> Unit,
    onShowInvite: () -> Unit,
    onMemberClick: (GroupMember) -> Unit,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 멤버 로드 실패 — 탭 자체가 자기 로드를 소유하므로 여기서 재시도를 준다
        if (uiState.error != null) {
            item(key = "members-error", span = { GridItemSpan(maxLineSpan) }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(uiState.error, style = SgTheme.typography.bodyMedium, color = sg.rust)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onRetry) {
                        Text("다시 시도", color = sg.accent)
                    }
                }
            }
        }
        if (uiState.isLoading && uiState.members.isEmpty()) {
            item(key = "members-loading", span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = sg.accent)
                }
            }
        }
        if (uiState.joinRequests.isNotEmpty()) {
            item(key = "join-requests", span = { GridItemSpan(maxLineSpan) }) {
                JoinRequestInbox(
                    requests = uiState.joinRequests,
                    processingUserId = uiState.processingRequestUserId,
                    actionError = uiState.actionError,
                    onApprove = onApprove,
                    onReject = onReject
                )
            }
        }
        // 모더레이터 전용 초대코드 만들기 — 승인 우회 가입 경로라 인박스와 같은 조정 도구로 묶는다
        if (canModerate) {
            item(key = "invite-code", span = { GridItemSpan(maxLineSpan) }) {
                OutlinedButton(
                    onClick = onShowInvite,
                    shape = SgTheme.shapes.button,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("초대코드 만들기", style = SgTheme.typography.labelLarge, color = sg.accent)
                }
            }
        }
        item(key = "member-count", span = { GridItemSpan(maxLineSpan) }) {
            Text("멤버 ${uiState.visibleMembers.size}", style = SgTheme.typography.titleSmall, color = sg.ink)
        }
        items(uiState.visibleMembers, key = GroupMember::userId) { member ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                // 본인 포함 전원 탭 가능 — 프로필 진입(본인=프로필 수정, DM 버튼은 타인에게만 보인다)
                modifier = Modifier.clickable { onMemberClick(member) }
            ) {
                SgAvatar(member.name, imageUrl = member.profileImg)
                Spacer(Modifier.height(4.dp))
                Text(
                    member.name,
                    style = SgTheme.typography.labelSmall,
                    color = sg.inkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
