package kr.hhp227.storygroup.ui.screens.notification

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cash.paging.LoadStateError
import app.cash.paging.LoadStateLoading
import app.cash.paging.compose.collectAsLazyPagingItems
import app.cash.paging.compose.itemKey
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kr.hhp227.storygroup.di.sessionViewModel
import kr.hhp227.storygroup.shared.domain.model.AppNotification
import kr.hhp227.storygroup.shared.domain.model.NotificationType
import kr.hhp227.storygroup.ui.components.SgEmptyState
import kr.hhp227.storygroup.ui.components.SgPagingFooter
import kr.hhp227.storygroup.ui.navigation.NavigationAction
import kr.hhp227.storygroup.ui.navigation.sessionNavigationViewModel
import kr.hhp227.storygroup.ui.theme.SgTheme
import kr.hhp227.storygroup.ui.util.formatRelativeTime

/**
 * 알림 — 미읽음 헤더(N건+모두 읽음 처리)+풀블리드 행 목록(미읽음=linen, 행 사이 헤어라인).
 * 서버가 target에서 역추적한 컨텍스트(그룹명·게시글 미리보기)를 둘째 줄에 그린다 — 행위자는 서버가
 * 저장하지 않아 여전히 없다. 게시글 컨텍스트가 풀린 행은 클릭 시 읽음 처리 후 게시글 상세로 이동
 * (풀리지 않은 행 — 그룹류/삭제된 대상 — 은 표시만).
 * 셸 목적지라 상단바는 셸이 소유. VM은 세션 스코프(추후 셸 종 아이콘 뱃지와 공유 대비).
 * iosApp NotificationsView.swift와 1:1 미러
 */
@Composable
fun NotificationsScreen(
    modifier: Modifier = Modifier,
    onNavigationAction: (NavigationAction) -> Unit = sessionNavigationViewModel()::onAction,
    viewModel: NotificationsViewModel = sessionNotificationsViewModel()
) {
    NotificationsContent(
        viewModel = viewModel,
        onOpenPost = { groupId, postId -> onNavigationAction(NavigationAction.NavigateToPostDetail(groupId, postId)) },
        modifier = modifier
    )
}

/** 세션 공유 알림 VM — 알림 화면과 셸 종 아이콘 뱃지(탭/드로어/홈/그룹)가 같은 인스턴스를 쓴다 */
@Composable
fun sessionNotificationsViewModel(): NotificationsViewModel = sessionViewModel {
    NotificationsViewModel(
        getNotificationsPagingDataUseCase = it.getNotificationsPagingDataUseCase,
        getUnreadNotificationCountUseCase = it.getUnreadNotificationCountUseCase,
        markNotificationAsReadUseCase = it.markNotificationAsReadUseCase,
        markAllNotificationsAsReadUseCase = it.markAllNotificationsAsReadUseCase,
        observePersonalEventsUseCase = it.observePersonalEventsUseCase
    )
}

@Composable
private fun NotificationsContent(
    viewModel: NotificationsViewModel,
    onOpenPost: (groupId: Long, postId: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val onAction = viewModel::onAction
    val sg = SgTheme.colors
    val pagingDataFlow = remember(viewModel) {
        viewModel.uiState.map { it.pagingData }.distinctUntilChanged()
    }
    val lazyPagingItems = pagingDataFlow.collectAsLazyPagingItems()

    // 이벤트 구독을 진입 Refresh보다 먼저 선언 — 같은 프레임에 발화되는 RefreshList를 놓치지 않는다
    LaunchedEffect(Unit) {
        viewModel.event.collect { event ->
            when (event) {
                NotificationsViewModel.Event.RefreshList -> lazyPagingItems.refresh()
            }
        }
    }
    // 진입 시 신선화 — 세션 스코프 VM이라 재진입 때 미읽음 수+목록을 함께 최신화한다
    LaunchedEffect(viewModel) {
        viewModel.onAction(NotificationsViewModel.Action.Refresh)
    }
    Column(modifier.fillMaxSize().background(sg.paper)) {
        // 웹 notification-list 미러 — 미읽음이 있을 때만 헤더 행 노출
        if (uiState.unreadCount > 0) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "미읽음 ${uiState.unreadCount}건",
                    style = SgTheme.typography.bodySmall,
                    color = sg.inkSoft,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { onAction(NotificationsViewModel.Action.MarkAllAsRead) },
                    enabled = !uiState.isMarkingAll
                ) {
                    Text("모두 읽음 처리", style = SgTheme.typography.labelLarge, color = sg.accent)
                }
            }
        }
        uiState.actionError?.let {
            Text(
                it,
                style = SgTheme.typography.bodySmall,
                color = sg.rust,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        // 풀블리드 목록 — 행이 화면 폭 전체를 쓰므로 가로 contentPadding 없이 행 내부 패딩만 둔다
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            val refreshState = lazyPagingItems.loadState.refresh
            val appendState = lazyPagingItems.loadState.append

            when {
                lazyPagingItems.itemCount == 0 && refreshState is LoadStateLoading -> item(key = "notifications-loading") {
                    Box(Modifier.fillParentMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = sg.accent)
                    }
                }
                lazyPagingItems.itemCount == 0 && refreshState is LoadStateError -> item(key = "notifications-error") {
                    Column(
                        modifier = Modifier.fillParentMaxWidth().padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            refreshState.error.message ?: "알림을 불러오지 못했습니다.",
                            style = SgTheme.typography.bodyMedium,
                            color = sg.rust
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = lazyPagingItems::retry) {
                            Text("다시 시도", color = sg.accent)
                        }
                    }
                }
                lazyPagingItems.itemCount == 0 -> item(key = "notifications-empty") {
                    SgEmptyState(
                        title = "알림이 없습니다",
                        subtitle = "새 소식이 생기면 여기에 표시됩니다.",
                        icon = Icons.Default.Notifications,
                        modifier = Modifier.fillParentMaxWidth().padding(vertical = 48.dp)
                    )
                }
                else -> {
                    items(count = lazyPagingItems.itemCount, key = lazyPagingItems.itemKey(AppNotification::id)) { index ->
                        lazyPagingItems[index]?.let { notification ->
                            NotificationRow(
                                notification = notification,
                                isRead = uiState.isRead(notification),
                                // VM이 한 건씩만 처리하므로 처리 중엔 모든 행의 버튼을 잠근다
                                enabled = uiState.processingId == null,
                                isProcessing = uiState.processingId == notification.id,
                                onMarkAsRead = {
                                    onAction(NotificationsViewModel.Action.MarkAsRead(notification.id))
                                },
                                onOpenPost = onOpenPost
                            )
                        }
                    }
                    if (appendState is LoadStateLoading || appendState is LoadStateError) {
                        item(key = "notifications-footer") {
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
}

/**
 * 알림 한 행(풀블리드) — 타입 아이콘 메달리온+라벨+컨텍스트(그룹명 · 게시글 미리보기)+상대시각,
 * 미읽음=linen 배경·읽음=흐리게, 행 아래 헤어라인. 미읽음에만 읽음 버튼.
 * 게시글 컨텍스트가 풀린 행만 클릭 가능 — 읽음 처리(가능할 때) 후 게시글 상세로 이동
 */
@Composable
private fun NotificationRow(
    notification: AppNotification,
    isRead: Boolean,
    enabled: Boolean,
    isProcessing: Boolean,
    onMarkAsRead: () -> Unit,
    onOpenPost: (groupId: Long, postId: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors
    // 스마트 캐스트용 로컬 캡처 — shared 모듈 프로퍼티라 널 검사 후에도 직접 참조는 캐스트가 안 된다
    val groupId = notification.groupId
    val postId = notification.postId

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .let { m ->
                    if (groupId != null && postId != null) {
                        m.clickable {
                            // 탭=소비: 다른 건 처리 중이 아니면 읽음 처리까지 함께(웹 알림 UX 관례)
                            if (!isRead && enabled) onMarkAsRead()
                            onOpenPost(groupId, postId)
                        }
                    } else m
                }
                .background(if (isRead) Color.Transparent else sg.linen)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .alpha(if (isRead) 0.6f else 1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(36.dp).background(sg.accentSoft, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(typeIcon(notification.type), contentDescription = null, tint = sg.accent, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    typeLabel(notification.type),
                    style = SgTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = sg.ink
                )
                contextLine(notification)?.let {
                    Text(
                        it,
                        style = SgTheme.typography.bodySmall,
                        color = sg.inkSoft,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    formatRelativeTime(notification.createdAt),
                    style = SgTheme.typography.labelSmall,
                    color = sg.inkFaint
                )
            }
            if (!isRead) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(horizontal = 12.dp).size(14.dp),
                        strokeWidth = 2.dp,
                        color = sg.inkFaint
                    )
                } else {
                    TextButton(onClick = onMarkAsRead, enabled = enabled) {
                        Text("읽음", color = if (enabled) sg.accent else sg.inkFaint)
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(sg.stoneBorder))
    }
}

/** 컨텍스트 줄 — 어떤 그룹/게시글의 알림인지. 서버가 못 푼 참조(삭제 등)는 null이라 줄째 숨긴다 */
private fun contextLine(notification: AppNotification): String? {
    val group = notification.groupName
    val preview = notification.postPreview

    return when {
        group != null && preview != null -> "$group · $preview"
        preview != null -> preview
        group != null -> group
        else -> null
    }
}

/** 타입 아이콘 — iosApp typeIcon(SF Symbol)과 1:1 의미 매핑 */
private fun typeIcon(type: NotificationType): ImageVector = when (type) {
    NotificationType.NEW_POST -> Icons.Default.Description
    NotificationType.COMMENT -> Icons.AutoMirrored.Filled.Chat
    NotificationType.LIKE -> Icons.Default.Favorite
    NotificationType.MENTION -> Icons.Default.AlternateEmail
    NotificationType.CHAT -> Icons.Default.Forum
    NotificationType.MEETING_STARTED -> Icons.Default.Videocam
    NotificationType.NOTICE -> Icons.Default.Campaign
    NotificationType.INVITE -> Icons.Default.Email
    NotificationType.JOIN_REQUEST -> Icons.Default.PersonAdd
    NotificationType.JOIN_APPROVED -> Icons.Default.CheckCircle
    NotificationType.JOIN_REJECTED -> Icons.Default.Cancel
}

/** 웹 TYPE_LABEL 미러 — iosApp typeLabel과 동일 */
private fun typeLabel(type: NotificationType): String = when (type) {
    NotificationType.NEW_POST -> "새 게시글"
    NotificationType.COMMENT -> "댓글"
    NotificationType.LIKE -> "좋아요"
    NotificationType.MENTION -> "멘션"
    NotificationType.CHAT -> "채팅 메시지"
    NotificationType.MEETING_STARTED -> "화상회의 시작"
    NotificationType.NOTICE -> "공지"
    NotificationType.INVITE -> "초대"
    NotificationType.JOIN_REQUEST -> "가입 신청"
    NotificationType.JOIN_APPROVED -> "가입 승인"
    NotificationType.JOIN_REJECTED -> "가입 거절"
}
