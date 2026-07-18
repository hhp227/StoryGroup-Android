package kr.hhp227.storygroup.ui.screens.friend

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
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kr.hhp227.storygroup.ui.components.SgAvatar
import kr.hhp227.storygroup.ui.components.SgCard
import kr.hhp227.storygroup.ui.theme.SgTheme

/** 친구 API 연동 전 표시용 모델 — TODO: shared에 FriendRepository/FriendsViewModel 추가 후 교체 */
data class FriendUiModel(
    val id: Long,
    val name: String,
    val email: String
)

private val sampleFriends = listOf(
    FriendUiModel(1, "김재환", "jaehwan@example.com"),
    FriendUiModel(2, "이수진", "sujin@example.com"),
    FriendUiModel(3, "박민준", "minjun@example.com")
)

/** 친구 목록 — 웹 /search(친구 기본 화면) 미러, 단방향 등록 구조 */
@Composable
fun FriendsScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(sampleFriends, key = { it.id }) { friend ->
            FriendRow(friend)
        }
    }
}

@Composable
private fun FriendRow(friend: FriendUiModel, modifier: Modifier = Modifier) {
    val sg = SgTheme.colors

    SgCard(modifier = modifier.fillMaxWidth(), onClick = { /* TODO: 공개 프로필 */ }) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SgAvatar(friend.name)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(friend.name, style = SgTheme.typography.titleSmall, color = sg.ink)
                Spacer(Modifier.height(2.dp))
                Text(friend.email, style = SgTheme.typography.bodySmall, color = sg.inkFaint)
            }
        }
    }
}
