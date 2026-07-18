package kr.hhp227.storygroup.ui.screens.chat

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kr.hhp227.storygroup.ui.components.SgAvatar
import kr.hhp227.storygroup.ui.components.SgCard
import kr.hhp227.storygroup.ui.components.SgSectionTitle
import kr.hhp227.storygroup.ui.theme.SgTheme

/** 채팅 허브 API(GET /api/chat-rooms, 라운지 제외) 연동 전 표시용 모델 — TODO: shared로 교체 */
data class ChatRoomUiModel(
    val id: Long,
    val name: String,
    val lastMessage: String,
    val lastMessageAt: String,
    val unreadCount: Int,
    val isGroup: Boolean
)

private val sampleRooms = listOf(
    ChatRoomUiModel(1, "등산 모임", "이번 주말 코스 공유합니다", "오후 2:41", 3, isGroup = true),
    ChatRoomUiModel(2, "스터디 그룹", "다음 발표 자료 올렸어요", "오전 11:02", 0, isGroup = true),
    ChatRoomUiModel(3, "김재환", "사진 고마워요!", "어제", 1, isGroup = false),
    ChatRoomUiModel(4, "이수진", "네 내일 봬요", "월요일", 0, isGroup = false)
)

/** 채팅 허브 — 웹 /dm 미러(그룹 채팅 + 다이렉트 메시지, 라운지 제외) */
@Composable
fun ChatScreen(modifier: Modifier = Modifier) {
    val groupRooms = sampleRooms.filter { it.isGroup }
    val directRooms = sampleRooms.filter { !it.isGroup }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "group-title") { SgSectionTitle("그룹 채팅") }
        items(groupRooms, key = { it.id }) { room -> ChatRoomRow(room) }

        item(key = "dm-title") {
            Spacer(Modifier.height(12.dp))
            SgSectionTitle("다이렉트 메시지")
        }
        items(directRooms, key = { it.id }) { room -> ChatRoomRow(room) }
    }
}

@Composable
private fun ChatRoomRow(room: ChatRoomUiModel, modifier: Modifier = Modifier) {
    val sg = SgTheme.colors

    SgCard(modifier = modifier.fillMaxWidth(), onClick = { /* TODO: 채팅방 입장 */ }) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SgAvatar(
                room.name,
                containerColor = if (room.isGroup) sg.accent2Soft else sg.accentSoft,
                contentColor = if (room.isGroup) sg.accent2 else sg.accent
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(room.name, style = SgTheme.typography.titleSmall, color = sg.ink)
                Spacer(Modifier.height(2.dp))
                Text(
                    room.lastMessage,
                    style = SgTheme.typography.bodySmall,
                    color = sg.inkSoft,
                    maxLines = 1
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(room.lastMessageAt, style = SgTheme.typography.bodySmall, color = sg.inkFaint)
                if (room.unreadCount > 0) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${room.unreadCount}",
                        style = SgTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = sg.accent
                    )
                }
            }
        }
    }
}
