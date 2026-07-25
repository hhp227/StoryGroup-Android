package kr.hhp227.storygroup.ui.screens.chat

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kr.hhp227.storygroup.di.sessionViewModel
import kr.hhp227.storygroup.ui.components.SgAvatar
import kr.hhp227.storygroup.ui.components.SgCard
import kr.hhp227.storygroup.ui.components.SgEmptyState
import kr.hhp227.storygroup.ui.components.SgSectionTitle
import kr.hhp227.storygroup.ui.theme.SgTheme

/**
 * 채팅 허브 — 웹 /dm 미러(그룹 채팅 + 다이렉트 메시지, 라운지 제외).
 * 행 탭 시 채팅방 풀스크린 목적지로 이동 — 그룹 방은 그룹명, DM은 상대 이름이 제목이 된다.
 * iosApp ChatView.swift와 1:1 미러
 */
@Composable
fun ChatScreen(
    onOpenChatRoom: (chatRoomId: Long, groupId: Long?, title: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = sessionViewModel {
        ChatViewModel(
            getGroupChatRoomsUseCase = it.getGroupChatRoomsUseCase,
            getDirectRoomsUseCase = it.getDirectRoomsUseCase
        )
    }
) {
    val uiState by viewModel.uiState.collectAsState()
    val onAction = viewModel::onAction
    val sg = SgTheme.colors

    when {
        uiState.groupRooms.isEmpty() && uiState.directRooms.isEmpty() && uiState.isLoading ->
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = sg.accent)
            }
        uiState.groupRooms.isEmpty() && uiState.directRooms.isEmpty() && uiState.error != null ->
            Column(
                modifier = modifier.fillMaxSize().padding(vertical = 48.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(uiState.error.orEmpty(), style = SgTheme.typography.bodyMedium, color = sg.rust)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { onAction(ChatViewModel.Action.Refresh) }) {
                    Text("다시 시도", color = sg.accent)
                }
            }
        uiState.groupRooms.isEmpty() && uiState.directRooms.isEmpty() ->
            SgEmptyState(
                title = "채팅방이 없습니다",
                subtitle = "그룹에 가입하거나 친구에게 메시지를 보내보세요.",
                icon = Icons.AutoMirrored.Filled.Chat,
                modifier = modifier.fillMaxSize()
            )
        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (uiState.groupRooms.isNotEmpty()) {
                item(key = "group-title") { SgSectionTitle("그룹 채팅") }
                items(uiState.groupRooms, key = { "group-${it.id}" }) { room ->
                    ChatRoomRow(
                        title = room.groupName,
                        subtitle = room.name,
                        imageUrl = null,
                        isGroup = true,
                        onClick = { onOpenChatRoom(room.id, room.groupId, room.groupName) }
                    )
                }
            }
            if (uiState.directRooms.isNotEmpty()) {
                item(key = "dm-title") {
                    if (uiState.groupRooms.isNotEmpty()) Spacer(Modifier.height(12.dp))
                    SgSectionTitle("다이렉트 메시지")
                }
                items(uiState.directRooms, key = { "dm-${it.id}" }) { room ->
                    ChatRoomRow(
                        title = room.otherUserName,
                        subtitle = null,
                        imageUrl = room.otherUserProfileImg,
                        isGroup = false,
                        onClick = { onOpenChatRoom(room.id, null, room.otherUserName) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatRoomRow(
    title: String,
    subtitle: String?,
    imageUrl: String?,
    isGroup: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors

    SgCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SgAvatar(
                title,
                imageUrl = imageUrl,
                containerColor = if (isGroup) sg.accent2Soft else sg.accentSoft,
                contentColor = if (isGroup) sg.accent2 else sg.accent
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = SgTheme.typography.titleSmall, color = sg.ink)
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        style = SgTheme.typography.bodySmall,
                        color = sg.inkSoft,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
