package kr.hhp227.storygroup.ui.screens.group

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kr.hhp227.storygroup.di.screenViewModel
import kr.hhp227.storygroup.shared.domain.model.DiscoverGroup
import kr.hhp227.storygroup.ui.components.SgEmptyState
import kr.hhp227.storygroup.ui.components.SgPullRefreshBox
import kr.hhp227.storygroup.ui.components.SgTopBar
import kr.hhp227.storygroup.ui.navigation.NavigationAction
import kr.hhp227.storygroup.ui.navigation.sessionNavigationViewModel
import kr.hhp227.storygroup.ui.theme.SgTheme
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import storygroup.composeapp.generated.resources.Res
import storygroup.composeapp.generated.resources.common_back
import storygroup.composeapp.generated.resources.common_retry
import storygroup.composeapp.generated.resources.count_members_full
import storygroup.composeapp.generated.resources.groups_pending
import storygroup.composeapp.generated.resources.pending_empty_subtitle
import storygroup.composeapp.generated.resources.pending_empty_title
import storygroup.composeapp.generated.resources.request_cancel
import storygroup.composeapp.generated.resources.two_part_dot

/**
 * 가입 신청중 — 레거시 JoinRequestGroupFragment 미러(진입 스트립 가운데 칸에서 진입).
 * NavHost 풀스크린 목적지라 상단바는 화면이 소유하고, VM은 백스택 엔트리 스코프(방문마다 다시 로드).
 * 행에 바로 신청 취소를 두고 행 클릭은 없다 — 미가입 그룹이라 이동할 상세가 없다.
 * iosApp PendingGroupsView.swift와 1:1 미러
 */
@Composable
fun PendingGroupsScreen(
    modifier: Modifier = Modifier,
    onNavigationAction: (NavigationAction) -> Unit = sessionNavigationViewModel()::onAction,
    viewModel: PendingGroupsViewModel = screenViewModel(key = "pending-groups") {
        PendingGroupsViewModel(
            getMyJoinRequestedGroupsUseCase = it.getMyJoinRequestedGroupsUseCase,
            cancelJoinRequestUseCase = it.cancelJoinRequestUseCase
        )
    }
) {
    PendingGroupsContent(
        viewModel = viewModel,
        onBack = { onNavigationAction(NavigationAction.NavigateBack) },
        modifier = modifier
    )
}

@Composable
private fun PendingGroupsContent(
    viewModel: PendingGroupsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val onAction = viewModel::onAction
    val sg = SgTheme.colors

    Column(modifier.fillMaxSize().background(sg.paper)) {
        SgTopBar(
            title = stringResource(Res.string.groups_pending),
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.common_back))
                }
            }
        )
        // 스피너는 데이터가 이미 있는 갱신에만 돈다 — 첫 로드는 목록 중앙 스피너가 담당(그룹 탭과 동일)
        SgPullRefreshBox(
            refreshing = uiState.groups.isNotEmpty() && uiState.isLoading,
            onRefresh = { onAction(PendingGroupsViewModel.Action.Refresh) },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when {
                    uiState.groups.isEmpty() && uiState.isLoading -> item(key = "pending-loading") {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = sg.accent)
                        }
                    }
                    uiState.groups.isEmpty() && uiState.loadError != null -> item(key = "pending-error") {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(uiState.loadError!!, style = SgTheme.typography.bodyMedium, color = sg.rust)
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { onAction(PendingGroupsViewModel.Action.Refresh) }) {
                                Text(stringResource(Res.string.common_retry), color = sg.accent)
                            }
                        }
                    }
                    uiState.groups.isEmpty() -> item(key = "pending-empty") {
                        SgEmptyState(
                            title = stringResource(Res.string.pending_empty_title),
                            subtitle = stringResource(Res.string.pending_empty_subtitle),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp)
                        )
                    }
                    else -> {
                        if (uiState.cancelError != null) {
                            item(key = "pending-cancel-error") {
                                Text(uiState.cancelError!!, style = SgTheme.typography.bodySmall, color = sg.rust)
                            }
                        }
                        items(uiState.groups, key = DiscoverGroup::id) { group ->
                            PendingGroupRow(
                                group = group,
                                isCanceling = uiState.cancelingGroupId == group.id,
                                cancelEnabled = uiState.cancelingGroupId == null,
                                onCancel = { onAction(PendingGroupsViewModel.Action.CancelRequest(group.id)) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 신청중 그룹 한 줄 — 커버/이름/멤버·가입방식 + 신청 취소(동시에 하나만 처리). 상태는 화면 제목이 말하므로 행 배지는 없다 */
@Composable
private fun PendingGroupRow(
    group: DiscoverGroup,
    isCanceling: Boolean,
    cancelEnabled: Boolean,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors

    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(SgTheme.shapes.button)
                .let { if (group.image == null) it.background(groupCoverBrush(group.id, sg)) else it },
            contentAlignment = Alignment.Center
        ) {
            if (group.image != null) {
                AsyncImage(
                    model = group.image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(group.name.take(1), style = SgTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                group.name,
                style = SgTheme.typography.titleSmall,
                color = sg.ink,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // 그룹 찾기 목록 행과 동일한 요약 정보 미러(DiscoverGroupsScreen)
            Text(
                stringResource(
                    Res.string.two_part_dot,
                    pluralStringResource(Res.plurals.count_members_full, group.memberCount.toInt(), group.memberCount),
                    joinTypeLabel(group.joinType)
                ),
                style = SgTheme.typography.bodySmall,
                color = sg.inkSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        if (isCanceling) {
            CircularProgressIndicator(
                color = sg.accent,
                strokeWidth = 2.dp,
                modifier = Modifier.padding(horizontal = 12.dp).size(16.dp)
            )
        } else {
            TextButton(onClick = onCancel, enabled = cancelEnabled) {
                Text(stringResource(Res.string.request_cancel), color = sg.rust)
            }
        }
    }
}
