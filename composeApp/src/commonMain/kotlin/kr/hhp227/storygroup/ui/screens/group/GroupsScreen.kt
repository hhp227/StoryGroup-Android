package kr.hhp227.storygroup.ui.screens.group

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kr.hhp227.storygroup.shared.domain.model.Group
import kr.hhp227.storygroup.shared.domain.model.GroupRole
import kr.hhp227.storygroup.ui.components.SgCard
import kr.hhp227.storygroup.ui.components.SgEmptyState
import kr.hhp227.storygroup.ui.components.SgTopBar
import kr.hhp227.storygroup.ui.theme.SgColors
import kr.hhp227.storygroup.ui.theme.SgTheme

/**
 * 가입중인 그룹 목록 + 만들기/찾기 진입 — 웹 /groups 내 그룹 탭 미러(라운지 제외).
 * 상세는 App의 NavHost 풀스크린 목적지(onOpenGroup) — iosApp NavigationStack push 미러
 */
@Composable
fun GroupsScreen(
    viewModel: GroupsViewModel,
    onOpenGroup: (Group) -> Unit,
    onOpenNotifications: () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val onAction = viewModel::onAction
    val sg = SgTheme.colors

    // 그룹 탭은 상세(콜랩싱 헤더)와의 전환 때문에 셸이 아닌 화면이 상단바를 소유한다(홈과 동일)
    Column(modifier) {
        SgTopBar(
            title = "그룹",
            navigationIcon = navigationIcon,
            actions = {
                IconButton(onClick = onOpenNotifications) {
                    Icon(Icons.Default.Notifications, contentDescription = "알림")
                }
            }
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "actions") {
                GroupActionsRow()
            }
            when {
                uiState.groups.isEmpty() && uiState.isLoading -> item(key = "groups-loading") {
                    Box(
                        Modifier.fillParentMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = sg.accent)
                    }
                }
                uiState.groups.isEmpty() && uiState.error != null -> item(key = "groups-error") {
                    Column(
                        modifier = Modifier.fillParentMaxWidth().padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(uiState.error.orEmpty(), style = SgTheme.typography.bodyMedium, color = sg.rust)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { onAction(GroupsViewModel.Action.Refresh) }) {
                            Text("다시 시도", color = sg.accent)
                        }
                    }
                }
                uiState.groups.isEmpty() -> item(key = "groups-empty") {
                    SgEmptyState(
                        title = "아직 그룹이 없습니다",
                        subtitle = "새 그룹을 만들거나 그룹 찾기에서 참여해보세요.",
                        modifier = Modifier.fillParentMaxWidth().padding(vertical = 48.dp)
                    )
                }
                else -> items(uiState.groups, key = Group::id) { group ->
                    GroupCard(group, onClick = { onOpenGroup(group) })
                }
            }
        }
    }
}

@Composable
private fun GroupActionsRow(modifier: Modifier = Modifier) {
    val sg = SgTheme.colors

    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { /* TODO: 그룹 만들기 */ },
            modifier = Modifier.weight(1f),
            shape = SgTheme.shapes.button,
            border = BorderStroke(1.dp, sg.stoneBorder),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = sg.accent)
        ) {
            Text("그룹 만들기", fontWeight = FontWeight.Bold)
        }
        OutlinedButton(
            onClick = { /* TODO: 그룹 찾기 */ },
            modifier = Modifier.weight(1f),
            shape = SgTheme.shapes.button,
            border = BorderStroke(1.dp, sg.stoneBorder),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = sg.accent)
        ) {
            Text("그룹 찾기", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GroupCard(group: Group, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val sg = SgTheme.colors

    SgCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 웹 GroupCover 미러 — 커버 이미지 로딩(④) 전까지 그룹별 그라데이션+이니셜 폴백
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(groupCoverBrush(group.id, sg), SgTheme.shapes.button),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    group.name.take(1),
                    style = SgTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        group.name,
                        style = SgTheme.typography.titleMedium,
                        color = sg.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (group.myRole != GroupRole.MEMBER) {
                        Spacer(Modifier.width(8.dp))
                        RoleChip(group.myRole)
                    }
                }
                if (!group.description.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        group.description.orEmpty(),
                        style = SgTheme.typography.bodySmall,
                        color = sg.inkSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** 웹 roleLabel 미러 */
internal fun roleLabel(role: GroupRole): String = when (role) {
    GroupRole.OWNER -> "방장"
    GroupRole.ADMIN -> "부방장"
    GroupRole.MEMBER -> "멤버"
}

/** 역할 칩 — 웹 roleChipClass 미러(방장=accent, 부방장 등=accent2) */
@Composable
internal fun RoleChip(role: GroupRole, modifier: Modifier = Modifier) {
    val sg = SgTheme.colors

    Text(
        roleLabel(role),
        style = SgTheme.typography.labelSmall,
        color = if (role == GroupRole.OWNER) sg.accent else sg.accent2,
        modifier = modifier
            .background(if (role == GroupRole.OWNER) sg.accentSoft else sg.accent2Soft, SgTheme.shapes.button)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

/** 웹 GroupCover 폴백 미러 — COVER_COLORS[id%4] → accent2 그라데이션(135deg) */
internal fun groupCoverBrush(groupId: Long, sg: SgColors): Brush {
    val base = listOf(sg.accent, sg.accent2, sg.moss, sg.amber)[(groupId % 4).toInt()]
    return Brush.linearGradient(listOf(base, sg.accent2))
}
