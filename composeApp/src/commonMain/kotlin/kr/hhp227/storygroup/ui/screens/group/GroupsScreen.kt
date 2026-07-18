package kr.hhp227.storygroup.ui.screens.group

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kr.hhp227.storygroup.ui.components.SgAvatar
import kr.hhp227.storygroup.ui.components.SgCard
import kr.hhp227.storygroup.ui.theme.SgTheme

/** 그룹 API 연동 전 표시용 모델 — TODO: shared에 GroupRepository/GroupsViewModel 추가 후 교체 */
data class GroupUiModel(
    val id: Long,
    val name: String,
    val memberCount: Int,
    val recentActivity: String
)

private val sampleGroups = listOf(
    GroupUiModel(1, "등산 모임", 12, "새 글 2 · 오늘"),
    GroupUiModel(2, "스터디 그룹", 5, "새 일정 1 · 어제"),
    GroupUiModel(3, "맛집 탐방", 8, "새 사진 4 · 3일 전")
)

/** 가입중인 그룹 목록 + 만들기/찾기 진입 — 웹 /groups 미러 */
@Composable
fun GroupsScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "actions") {
            GroupActionsRow()
        }
        items(sampleGroups, key = { it.id }) { group ->
            GroupCard(group)
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
private fun GroupCard(group: GroupUiModel, modifier: Modifier = Modifier) {
    val sg = SgTheme.colors

    SgCard(modifier = modifier.fillMaxWidth(), onClick = { /* TODO: 그룹 상세 */ }) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SgAvatar(group.name, size = 48.dp, containerColor = sg.accent2Soft, contentColor = sg.accent2)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(group.name, style = SgTheme.typography.titleMedium, color = sg.ink)
                Spacer(Modifier.height(2.dp))
                Text("멤버 ${group.memberCount}명", style = SgTheme.typography.bodySmall, color = sg.inkSoft)
                Spacer(Modifier.height(2.dp))
                Text(group.recentActivity, style = SgTheme.typography.bodySmall, color = sg.inkFaint)
            }
        }
    }
}
