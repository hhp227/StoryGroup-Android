package kr.hhp227.storygroup.ui.screens.notification

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
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
import kr.hhp227.storygroup.ui.components.SgCard
import kr.hhp227.storygroup.ui.components.SgEmptyState
import kr.hhp227.storygroup.ui.components.SgPagingFooter
import kr.hhp227.storygroup.ui.theme.SgTheme
import kr.hhp227.storygroup.ui.util.formatRelativeTime

/**
 * 알림 — 웹 /notifications 미러: 미읽음 헤더(N건+모두 읽음 처리)+타입 라벨 목록.
 * 서버 응답엔 행위자/본문이 없어 타입 라벨+상대시각만 그리고, 클릭 이동도 웹처럼 아직 없다.
 * 셸 목적지라 상단바는 셸이 소유. VM은 세션 스코프(추후 셸 종 아이콘 뱃지와 공유 대비).
 * iosApp NotificationsView.swift와 1:1 미러
 */
@Composable
fun NotificationsScreen(
    modifier: Modifier = Modifier,
    viewModel: NotificationsViewModel = sessionNotificationsViewModel()
) {
    NotificationsContent(viewModel = viewModel, modifier = modifier)
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
private fun NotificationsContent(viewModel: NotificationsViewModel, modifier: Modifier = Modifier) {
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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                            NotificationCard(
                                notification = notification,
                                isRead = uiState.isRead(notification),
                                // VM이 한 건씩만 처리하므로 처리 중엔 모든 행의 버튼을 잠근다
                                enabled = uiState.processingId == null,
                                isProcessing = uiState.processingId == notification.id,
                                onMarkAsRead = {
                                    onAction(NotificationsViewModel.Action.MarkAsRead(notification.id))
                                }
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

/** 웹 notification-list 아이템 미러 — 타입 라벨+상대시각, 읽음은 흐리게, 미읽음에만 읽음 버튼 */
@Composable
private fun NotificationCard(
    notification: AppNotification,
    isRead: Boolean,
    enabled: Boolean,
    isProcessing: Boolean,
    onMarkAsRead: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors

    SgCard(modifier.fillMaxWidth().alpha(if (isRead) 0.6f else 1f)) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    typeLabel(notification.type),
                    style = SgTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = sg.ink
                )
                Text(
                    formatRelativeTime(notification.createdAt),
                    style = SgTheme.typography.labelSmall,
                    color = sg.inkFaint
                )
            }
            if (!isRead) {
                OutlinedButton(onClick = onMarkAsRead, enabled = enabled, shape = SgTheme.shapes.button) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(14.dp).height(14.dp),
                            strokeWidth = 2.dp,
                            color = sg.inkFaint
                        )
                    } else {
                        Text("읽음", color = if (enabled) sg.ink else sg.inkFaint)
                    }
                }
            }
        }
    }
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
