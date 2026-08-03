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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import kr.hhp227.storygroup.di.sessionViewModel
import kr.hhp227.storygroup.shared.domain.model.DiscoverGroup
import kr.hhp227.storygroup.shared.domain.model.DiscoverSort
import kr.hhp227.storygroup.shared.domain.model.GroupJoinType
import kr.hhp227.storygroup.shared.domain.model.GroupMembershipStatus
import kr.hhp227.storygroup.ui.components.SgCard
import kr.hhp227.storygroup.ui.components.SgEmptyState
import kr.hhp227.storygroup.ui.components.SgPagingFooter
import kr.hhp227.storygroup.ui.components.SgPrimaryButton
import kr.hhp227.storygroup.ui.components.SgTextField
import kr.hhp227.storygroup.ui.components.SgTopBar
import kr.hhp227.storygroup.ui.theme.SgTheme

@Composable
private fun discoverGroupsViewModel(): DiscoverGroupsViewModel {
    val container = LocalAppContainer.current

    return viewModel(key = "discover-groups") {
        DiscoverGroupsViewModel(
            getDiscoverGroupsPagingDataUseCase = container.getDiscoverGroupsPagingDataUseCase,
            joinGroupUseCase = container.joinGroupUseCase,
            joinGroupByCodeUseCase = container.joinGroupByCodeUseCase,
            cancelJoinRequestUseCase = container.cancelJoinRequestUseCase
        )
    }
}

/**
 * 그룹 찾기 — 검색+정렬(최신/인기), 카드 탭 시 상세 다이얼로그에서 가입/신청(웹 GroupDetailDialog 미러).
 * 검색 입력폼은 상단바 제목 자리에 둔다 — iOS .searchable(내비바 검색 필드)과 표시 위치 통일.
 * Paging 소유 화면이라 GroupsScreen과 동일한 Screen/Content 2계층. NavHost 풀스크린 목적지라 상단바는
 * 화면이 소유하고, VM은 백스택 엔트리 스코프(방문마다 새로 검색 — 세션 스코프 아님).
 * iosApp DiscoverGroupsView.swift와 1:1 미러
 */
@Composable
fun DiscoverGroupsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiscoverGroupsViewModel = discoverGroupsViewModel(),
    groupsViewModel: GroupsViewModel = sessionViewModel { GroupsViewModel(it.getMyGroupsPagingDataUseCase) }
) {
    DiscoverGroupsContent(
        viewModel = viewModel,
        onBack = onBack,
        onJoined = { groupsViewModel.onAction(GroupsViewModel.Action.Refresh) },
        modifier = modifier
    )
}

@Composable
private fun DiscoverGroupsContent(
    viewModel: DiscoverGroupsViewModel,
    onBack: () -> Unit,
    onJoined: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val onAction = viewModel::onAction
    val sg = SgTheme.colors
    var queryText by rememberSaveable { mutableStateOf("") }
    var selectedGroup by remember { mutableStateOf<DiscoverGroup?>(null) }
    var showJoinByCode by rememberSaveable { mutableStateOf(false) }

    val pagingDataFlow = remember(viewModel) {
        viewModel.uiState.map { it.pagingData }.distinctUntilChanged()
    }
    val lazyPagingItems = pagingDataFlow.collectAsLazyPagingItems()

    LaunchedEffect(Unit) {
        viewModel.event.collect { event ->
            when (event) {
                DiscoverGroupsViewModel.Event.Joined -> onJoined()
                DiscoverGroupsViewModel.Event.JoinedByCode -> {
                    showJoinByCode = false
                    onJoined()
                }
            }
        }
    }
    Column(modifier.fillMaxSize().background(sg.paper)) {
        SgTopBar(
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                }
            },
            actions = {
                IconButton(onClick = { onAction(DiscoverGroupsViewModel.Action.Search(queryText)) }) {
                    Icon(Icons.Default.Search, contentDescription = "검색", tint = sg.accent)
                }
            }
        ) {
            // 검색 입력폼을 제목 자리에 — iOS .searchable(내비바 검색 필드) 미러.
            // 실행은 IME 검색/우측 아이콘(제출 기반), 비우면 즉시 전체 목록 복귀(VM이 중복 검색은 걸러낸다)
            BasicTextField(
                value = queryText,
                onValueChange = {
                    queryText = it
                    if (it.isEmpty()) onAction(DiscoverGroupsViewModel.Action.Search(""))
                },
                modifier = Modifier.fillMaxWidth(),
                textStyle = SgTheme.typography.bodyLarge.copy(color = sg.ink),
                singleLine = true,
                cursorBrush = SolidColor(sg.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onAction(DiscoverGroupsViewModel.Action.Search(queryText)) }),
                decorationBox = { innerTextField ->
                    Box {
                        if (queryText.isEmpty()) {
                            Text("그룹 검색", style = SgTheme.typography.bodyLarge, color = sg.inkFaint)
                        }
                        innerTextField()
                    }
                }
            )
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SortToggleButton(
                    label = "최신순",
                    selected = uiState.sort == DiscoverSort.RECENT,
                    onClick = { onAction(DiscoverGroupsViewModel.Action.ChangeSort(DiscoverSort.RECENT)) }
                )
                SortToggleButton(
                    label = "인기순",
                    selected = uiState.sort == DiscoverSort.POPULAR,
                    onClick = { onAction(DiscoverGroupsViewModel.Action.ChangeSort(DiscoverSort.POPULAR)) }
                )
                Spacer(Modifier.weight(1f))
                // 웹 group-discover 헤더의 "초대 코드로 가입" 버튼 미러
                TextButton(onClick = { showJoinByCode = true }) {
                    Text("초대 코드로 가입", style = SgTheme.typography.labelLarge, color = sg.accent)
                }
            }
            uiState.error?.let {
                Text(it, style = SgTheme.typography.bodySmall, color = sg.rust)
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val refreshState = lazyPagingItems.loadState.refresh
            val appendState = lazyPagingItems.loadState.append

            when {
                lazyPagingItems.itemCount == 0 && refreshState is LoadStateLoading -> item(key = "discover-loading") {
                    Box(Modifier.fillParentMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = sg.accent)
                    }
                }
                lazyPagingItems.itemCount == 0 && refreshState is LoadStateError -> item(key = "discover-error") {
                    Column(
                        modifier = Modifier.fillParentMaxWidth().padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            refreshState.error.message ?: "그룹을 불러오지 못했습니다.",
                            style = SgTheme.typography.bodyMedium,
                            color = sg.rust
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = lazyPagingItems::retry) {
                            Text("다시 시도", color = sg.accent)
                        }
                    }
                }
                lazyPagingItems.itemCount == 0 -> item(key = "discover-empty") {
                    SgEmptyState(
                        title = "그룹을 찾지 못했습니다",
                        subtitle = "다른 검색어로 시도해보세요.",
                        modifier = Modifier.fillParentMaxWidth().padding(vertical = 48.dp)
                    )
                }
                else -> {
                    items(count = lazyPagingItems.itemCount, key = lazyPagingItems.itemKey(DiscoverGroup::id)) { index ->
                        lazyPagingItems[index]?.let { group ->
                            DiscoverGroupCard(
                                group = group,
                                membership = uiState.membershipOf(group),
                                onClick = { selectedGroup = group }
                            )
                        }
                    }
                    if (appendState is LoadStateLoading || appendState is LoadStateError) {
                        item(key = "discover-footer") {
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

    selectedGroup?.let { group ->
        GroupDetailDialog(
            group = group,
            membership = uiState.membershipOf(group),
            isLoading = uiState.joiningGroupId == group.id,
            onDismiss = { selectedGroup = null },
            onJoin = { onAction(DiscoverGroupsViewModel.Action.Join(group.id)) },
            onCancelRequest = { onAction(DiscoverGroupsViewModel.Action.CancelRequest(group.id)) }
        )
    }
    if (showJoinByCode) {
        JoinByCodeDialog(
            isLoading = uiState.isJoiningByCode,
            error = uiState.joinByCodeError,
            onDismiss = {
                showJoinByCode = false
                onAction(DiscoverGroupsViewModel.Action.DismissJoinByCodeError)
            },
            onJoin = { onAction(DiscoverGroupsViewModel.Action.JoinByCode(it)) }
        )
    }
}

/** 웹 JoinByCodeDialog 미러 — 전달받은 8자리 코드를 입력하면 승인 없이 바로 가입된다 */
@Composable
private fun JoinByCodeDialog(
    isLoading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onJoin: (String) -> Unit
) {
    val sg = SgTheme.colors
    var code by rememberSaveable { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        SgCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "초대 코드로 가입",
                        style = SgTheme.typography.titleMedium,
                        color = sg.ink,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "닫기", tint = sg.inkSoft)
                    }
                }
                Text(
                    "전달받은 8자리 초대 코드를 입력하면 바로 가입됩니다.",
                    style = SgTheme.typography.bodySmall,
                    color = sg.inkSoft
                )
                SgTextField(value = code, onValueChange = { code = it }, label = "초대 코드")
                error?.let {
                    Text(it, style = SgTheme.typography.bodySmall, color = sg.rust)
                }
                SgPrimaryButton(
                    text = "가입",
                    onClick = { onJoin(code) },
                    enabled = code.isNotBlank(),
                    isLoading = isLoading,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun SortToggleButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val sg = SgTheme.colors

    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier,
            shape = SgTheme.shapes.button,
            colors = ButtonDefaults.buttonColors(backgroundColor = sg.accent, contentColor = sg.onAccent)
        ) {
            Text(label, style = SgTheme.typography.labelLarge)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            shape = SgTheme.shapes.button,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = sg.inkSoft)
        ) {
            Text(label, style = SgTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun DiscoverGroupCard(
    group: DiscoverGroup,
    membership: GroupMembershipStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors

    SgCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (group.image != null) {
                AsyncImage(
                    model = group.image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp).clip(SgTheme.shapes.button)
                )
            } else {
                Box(
                    modifier = Modifier.size(48.dp).background(groupCoverBrush(group.id, sg), SgTheme.shapes.button),
                    contentAlignment = Alignment.Center
                ) {
                    Text(group.name.take(1), style = SgTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(group.name, style = SgTheme.typography.titleMedium, color = sg.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "멤버 ${group.memberCount}명 · ${joinTypeLabel(group.joinType)}",
                    style = SgTheme.typography.bodySmall,
                    color = sg.inkSoft
                )
            }
            Spacer(Modifier.width(8.dp))
            MembershipBadge(membership)
        }
    }
}

@Composable
private fun MembershipBadge(membership: GroupMembershipStatus, modifier: Modifier = Modifier) {
    val sg = SgTheme.colors
    val label = when (membership) {
        GroupMembershipStatus.MEMBER -> "가입됨"
        GroupMembershipStatus.PENDING -> "신청됨"
        GroupMembershipStatus.NONE -> return
    }

    Text(
        label,
        style = SgTheme.typography.labelSmall,
        color = sg.accent2,
        modifier = modifier
            .background(sg.accent2Soft, SgTheme.shapes.button)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

/** 웹 GroupDetailDialog 미러 — 커버/이름/전체 설명/멤버 수/가입 방식+상태별 가입 액션 */
@Composable
private fun GroupDetailDialog(
    group: DiscoverGroup,
    membership: GroupMembershipStatus,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onJoin: () -> Unit,
    onCancelRequest: () -> Unit
) {
    val sg = SgTheme.colors

    Dialog(onDismissRequest = onDismiss) {
        SgCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (group.image != null) {
                        AsyncImage(
                            model = group.image,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(56.dp).clip(SgTheme.shapes.button)
                        )
                    } else {
                        Box(
                            modifier = Modifier.size(56.dp).background(groupCoverBrush(group.id, sg), SgTheme.shapes.button),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(group.name.take(1), style = SgTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(group.name, style = SgTheme.typography.titleMedium, color = sg.ink, fontWeight = FontWeight.Bold)
                        Text(
                            "멤버 ${group.memberCount}명 · ${joinTypeLabel(group.joinType)}",
                            style = SgTheme.typography.bodySmall,
                            color = sg.inkSoft
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "닫기", tint = sg.inkSoft)
                    }
                }
                if (!group.description.isNullOrBlank()) {
                    Text(group.description.orEmpty(), style = SgTheme.typography.bodyMedium, color = sg.ink)
                }
                when (membership) {
                    GroupMembershipStatus.MEMBER -> SgPrimaryButton(text = "가입됨", onClick = {}, enabled = false)
                    GroupMembershipStatus.PENDING -> SgPrimaryButton(
                        text = "신청 취소",
                        onClick = onCancelRequest,
                        isLoading = isLoading
                    )
                    GroupMembershipStatus.NONE -> SgPrimaryButton(
                        text = if (group.joinType == GroupJoinType.AUTO_APPROVE) "가입" else "신청",
                        onClick = onJoin,
                        isLoading = isLoading
                    )
                }
            }
        }
    }
}

private fun joinTypeLabel(joinType: GroupJoinType): String = when (joinType) {
    GroupJoinType.AUTO_APPROVE -> "자동 승인"
    GroupJoinType.APPROVAL_REQUIRED -> "승인제"
}
