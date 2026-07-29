package kr.hhp227.storygroup.ui.screens.meeting

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.cash.paging.LoadStateError
import app.cash.paging.LoadStateLoading
import app.cash.paging.compose.collectAsLazyPagingItems
import app.cash.paging.compose.itemKey
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kr.hhp227.storygroup.di.LocalAppContainer
import kr.hhp227.storygroup.shared.domain.model.Meeting
import kr.hhp227.storygroup.ui.components.SgCard
import kr.hhp227.storygroup.ui.components.SgEmptyState
import kr.hhp227.storygroup.ui.components.SgPagingFooter
import kr.hhp227.storygroup.ui.components.SgPrimaryButton
import kr.hhp227.storygroup.ui.components.SgPullRefreshBox
import kr.hhp227.storygroup.ui.components.SgTopBar
import kr.hhp227.storygroup.ui.theme.SgTheme
import kr.hhp227.storygroup.ui.util.formatRelativeTime

@Composable
private fun meetingsViewModel(groupId: Long): MeetingsViewModel {
    val container = LocalAppContainer.current

    return viewModel(key = "meetings-$groupId") {
        MeetingsViewModel(
            groupId = groupId,
            createMeetingUseCase = container.createMeetingUseCase,
            getGroupMeetingsPagingDataUseCase = container.getGroupMeetingsPagingDataUseCase
        )
    }
}

/**
 * 그룹 화상회의 목록 — 시작 시각 내림차순 페이징 + "회의 시작"(멤버 누구나, 웹 미러).
 * NavHost 풀스크린 목적지라 상단바는 화면이 소유하고, VM은 백스택 엔트리 스코프.
 * iosApp MeetingsView.swift와 1:1 미러
 */
@Composable
fun MeetingsScreen(
    groupId: Long,
    onBack: () -> Unit,
    onOpenMeeting: (meetingId: Long) -> Unit,
    modifier: Modifier = Modifier,
    // 라우트(백스택 엔트리) 스코프 — pop되면 함께 정리된다(ConCafe CafeScreen 패턴)
    viewModel: MeetingsViewModel = meetingsViewModel(groupId)
) {
    MeetingsContent(
        viewModel = viewModel,
        onBack = onBack,
        onOpenMeeting = onOpenMeeting,
        modifier = modifier
    )
}

@Composable
private fun MeetingsContent(
    viewModel: MeetingsViewModel,
    onBack: () -> Unit,
    onOpenMeeting: (meetingId: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val onAction = viewModel::onAction
    val sg = SgTheme.colors
    // 상태에서 pagingData만 뽑아낸 스트림을 수집 — Paging-CRUD 샘플·iOS($state.map)와 동일 관용구
    val pagingDataFlow = remember(viewModel) {
        viewModel.uiState.map { it.pagingData }.distinctUntilChanged()
    }
    val lazyPagingItems = pagingDataFlow.collectAsLazyPagingItems()
    val refreshState = lazyPagingItems.loadState.refresh
    val appendState = lazyPagingItems.loadState.append

    LaunchedEffect(Unit) {
        viewModel.event.collect { event ->
            when (event) {
                is MeetingsViewModel.Event.MeetingCreated -> {
                    // 새 회의가 목록 맨 위로 오도록 갱신하고 상세로 바로 들어간다
                    lazyPagingItems.refresh()
                    onOpenMeeting(event.meetingId)
                }
            }
        }
    }
    Column(modifier.fillMaxSize().background(sg.paper)) {
        SgTopBar(
            title = "화상회의",
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                }
            }
        )
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SgPrimaryButton(
                text = "회의 시작",
                onClick = { onAction(MeetingsViewModel.Action.CreateMeeting) },
                isLoading = uiState.isCreating,
                modifier = Modifier.fillMaxWidth()
            )
            uiState.actionError?.let {
                Text(it, style = SgTheme.typography.bodySmall, color = sg.rust)
            }
        }
        // 스피너는 데이터가 이미 있는 갱신에만 돈다 — 첫 로드는 목록 중앙 스피너가 담당(홈과 동일)
        SgPullRefreshBox(
            refreshing = lazyPagingItems.itemCount > 0 && refreshState is LoadStateLoading,
            onRefresh = lazyPagingItems::refresh
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when {
                    lazyPagingItems.itemCount == 0 && refreshState is LoadStateLoading -> item(key = "meetings-loading") {
                        Box(Modifier.fillParentMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = sg.accent)
                        }
                    }
                    lazyPagingItems.itemCount == 0 && refreshState is LoadStateError -> item(key = "meetings-error") {
                        Column(
                            modifier = Modifier.fillParentMaxWidth().padding(vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                refreshState.error.message ?: "회의 목록을 불러오지 못했습니다.",
                                style = SgTheme.typography.bodyMedium,
                                color = sg.rust
                            )
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = lazyPagingItems::retry) {
                                Text("다시 시도", color = sg.accent)
                            }
                        }
                    }
                    lazyPagingItems.itemCount == 0 -> item(key = "meetings-empty") {
                        SgEmptyState(
                            title = "아직 회의가 없습니다",
                            subtitle = "회의 시작을 누르면 그룹원에게 알림이 갑니다.",
                            icon = Icons.Default.Videocam,
                            modifier = Modifier.fillParentMaxWidth().padding(vertical = 48.dp)
                        )
                    }
                    else -> {
                        items(count = lazyPagingItems.itemCount, key = lazyPagingItems.itemKey(Meeting::id)) { index ->
                            lazyPagingItems[index]?.let { meeting ->
                                MeetingCard(meeting = meeting, onClick = { onOpenMeeting(meeting.id) })
                            }
                        }
                        if (appendState is LoadStateLoading || appendState is LoadStateError) {
                            item(key = "meetings-footer") {
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
}

@Composable
private fun MeetingCard(meeting: Meeting, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val sg = SgTheme.colors

    SgCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).background(sg.accentSoft, SgTheme.shapes.button),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Videocam, contentDescription = null, tint = sg.accent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "회의 #${meeting.id}",
                    style = SgTheme.typography.titleMedium,
                    color = sg.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "시작 ${formatRelativeTime(meeting.startedAt)}",
                    style = SgTheme.typography.bodySmall,
                    color = sg.inkSoft
                )
            }
            Spacer(Modifier.width(8.dp))
            MeetingStatusBadge(isActive = meeting.isActive)
        }
    }
}

@Composable
internal fun MeetingStatusBadge(isActive: Boolean, modifier: Modifier = Modifier) {
    val sg = SgTheme.colors

    if (isActive) {
        Text(
            "진행 중",
            style = SgTheme.typography.labelSmall,
            color = sg.accent2,
            fontWeight = FontWeight.Bold,
            modifier = modifier
                .background(sg.accent2Soft, SgTheme.shapes.button)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    } else {
        Text(
            "종료됨",
            style = SgTheme.typography.labelSmall,
            color = sg.inkSoft,
            modifier = modifier
                .background(sg.linen, SgTheme.shapes.button)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
