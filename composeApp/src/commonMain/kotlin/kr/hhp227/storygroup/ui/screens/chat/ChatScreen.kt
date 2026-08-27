package kr.hhp227.storygroup.ui.screens.chat

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kr.hhp227.storygroup.di.sessionViewModel
import kr.hhp227.storygroup.ui.components.SgAvatar
import kr.hhp227.storygroup.ui.components.SgEmptyState
import kr.hhp227.storygroup.ui.components.SgSectionTitle
import kr.hhp227.storygroup.ui.components.SgUnreadBadge
import kr.hhp227.storygroup.ui.navigation.NavigationAction
import kr.hhp227.storygroup.ui.navigation.sessionNavigationViewModel
import kr.hhp227.storygroup.ui.theme.SgTheme
import kr.hhp227.storygroup.ui.util.formatRelativeTime
import org.jetbrains.compose.resources.stringResource
import storygroup.composeapp.generated.resources.Res
import storygroup.composeapp.generated.resources.chat_empty_subtitle
import storygroup.composeapp.generated.resources.chat_empty_title
import storygroup.composeapp.generated.resources.chat_no_messages_yet
import storygroup.composeapp.generated.resources.chat_section_dm
import storygroup.composeapp.generated.resources.chat_section_group
import storygroup.composeapp.generated.resources.common_file
import storygroup.composeapp.generated.resources.common_photo
import storygroup.composeapp.generated.resources.common_retry

/**
 * 채팅 허브 — 웹 /dm 미러(그룹 채팅 + 다이렉트 메시지, 라운지 제외).
 * 행 탭 시 채팅방 풀스크린 목적지로 이동 — 그룹 방은 그룹명, DM은 상대 이름이 제목이 된다.
 * iosApp ChatView.swift와 1:1 미러
 */
@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    onNavigationAction: (NavigationAction) -> Unit = sessionNavigationViewModel()::onAction,
    viewModel: ChatViewModel = sessionChatViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val onAction = viewModel::onAction
    val sg = SgTheme.colors
    val onOpenChatRoom = { chatRoomId: Long, groupId: Long?, title: String ->
        onNavigationAction(NavigationAction.NavigateToChatRoom(chatRoomId, groupId, title))
    }

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
                    Text(stringResource(Res.string.common_retry), color = sg.accent)
                }
            }
        uiState.groupRooms.isEmpty() && uiState.directRooms.isEmpty() ->
            SgEmptyState(
                title = stringResource(Res.string.chat_empty_title),
                subtitle = stringResource(Res.string.chat_empty_subtitle),
                icon = Icons.AutoMirrored.Filled.Chat,
                modifier = modifier.fillMaxSize()
            )
        // 카카오톡식 풀블리드 행 — 카드 없이 행이 자체 패딩을 갖고, 섹션 제목만 좌우 여백을 준다
        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            if (uiState.groupRooms.isNotEmpty()) {
                item(key = "group-title") { SgSectionTitle(stringResource(Res.string.chat_section_group), Modifier.padding(horizontal = 16.dp)) }
                items(uiState.groupRooms, key = { "group-${it.id}" }) { room ->
                    ChatRoomRow(
                        title = room.groupName,
                        roomName = room.name,
                        imageUrl = null,
                        isGroup = true,
                        unreadCount = room.unreadCount,
                        lastMessageText = room.lastMessageText,
                        lastMessageType = room.lastMessageType,
                        lastMessageAt = room.lastMessageAt,
                        onClick = { onOpenChatRoom(room.id, room.groupId, room.groupName) }
                    )
                }
            }
            if (uiState.directRooms.isNotEmpty()) {
                item(key = "dm-title") {
                    if (uiState.groupRooms.isNotEmpty()) Spacer(Modifier.height(12.dp))
                    SgSectionTitle(stringResource(Res.string.chat_section_dm), Modifier.padding(horizontal = 16.dp))
                }
                items(uiState.directRooms, key = { "dm-${it.id}" }) { room ->
                    ChatRoomRow(
                        title = room.otherUserName,
                        roomName = null,
                        imageUrl = room.otherUserProfileImg,
                        isGroup = false,
                        unreadCount = room.unreadCount,
                        lastMessageText = room.lastMessageText,
                        lastMessageType = room.lastMessageType,
                        lastMessageAt = room.lastMessageAt,
                        onClick = { onOpenChatRoom(room.id, null, room.otherUserName) }
                    )
                }
            }
        }
    }
}

/** 세션 공유 채팅 허브 VM — 허브 화면·셸 채팅 탭 뱃지·채팅방 진입/이탈 신호가 같은 인스턴스를 쓴다 */
@Composable
fun sessionChatViewModel(): ChatViewModel = sessionViewModel {
    ChatViewModel(
        getGroupChatRoomsUseCase = it.getGroupChatRoomsUseCase,
        getDirectRoomsUseCase = it.getDirectRoomsUseCase,
        observePersonalEventsUseCase = it.observePersonalEventsUseCase
    )
}

/** 채팅방 한 줄 — 카카오톡식(아바타 + 제목·마지막 메시지 2줄 + 우측 시각·미읽음 버블), 카드 없음 */
@Composable
private fun ChatRoomRow(
    title: String,
    roomName: String?,
    imageUrl: String?,
    isGroup: Boolean,
    unreadCount: Long,
    lastMessageText: String?,
    lastMessageType: String?,
    lastMessageAt: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SgAvatar(
            title,
            size = 52.dp,
            imageUrl = imageUrl,
            containerColor = if (isGroup) sg.accent2Soft else sg.accentSoft,
            contentColor = if (isGroup) sg.accent2 else sg.accent
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = SgTheme.typography.titleSmall,
                    color = sg.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (roomName != null) {
                    Spacer(Modifier.width(4.dp))
                    Text(roomName, style = SgTheme.typography.bodySmall, color = sg.inkSoft, maxLines = 1)
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                messagePreview(lastMessageText, lastMessageType, lastMessageAt),
                style = SgTheme.typography.bodySmall,
                color = sg.inkSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (lastMessageAt != null) {
                Text(formatRelativeTime(lastMessageAt), style = SgTheme.typography.labelSmall, color = sg.inkSoft)
            }
            SgUnreadBadge(unreadCount)
        }
    }
}

/** 미리보기 라벨 — 첨부 전용 메시지(text 빈 문자열)는 종류로 표기한다. iosApp ChatView.swift preview와 1:1 미러 */
@Composable
private fun messagePreview(text: String?, attachmentType: String?, lastMessageAt: String?): String = when {
    lastMessageAt == null -> stringResource(Res.string.chat_no_messages_yet)
    !text.isNullOrEmpty() -> text
    attachmentType?.startsWith("image/") == true -> stringResource(Res.string.common_photo)
    else -> stringResource(Res.string.common_file)
}
