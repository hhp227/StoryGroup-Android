package kr.hhp227.storygroup.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kr.hhp227.storygroup.di.LocalAppContainer
import kr.hhp227.storygroup.shared.domain.model.ChatMessage
import kr.hhp227.storygroup.ui.components.SgAvatar
import kr.hhp227.storygroup.ui.components.SgEmptyState
import kr.hhp227.storygroup.ui.components.SgTextField
import kr.hhp227.storygroup.ui.components.SgTopBar
import kr.hhp227.storygroup.ui.theme.SgTheme
import kr.hhp227.storygroup.ui.util.rememberFilePickerLauncher
import kr.hhp227.storygroup.ui.util.rememberImagePickerLauncher
import kotlin.math.roundToInt

/**
 * 채팅방 — 웹 MessageThread 미러(말풍선 정렬·작성자 변경 시에만 아바타/이름·첨부 렌더링,
 * 웹처럼 시각 표기는 없음). 목록은 웹처럼 상단부터 채우는 일반 목록(위=과거, 아래=최신)이고
 * 진입/새 메시지 때 맨 아래로 스크롤한다. 상단 근처에 닿으면 이전 페이지를 자동 로드한다
 * (무한 스크롤 — 웹엔 없는 앱 확장). iosApp ChatRoomView.swift와 1:1 미러
 */
@Composable
fun ChatRoomScreen(
    chatRoomId: Long,
    groupId: Long?,
    title: String,
    onBack: () -> Unit,
    // video=false는 보이스톡(카메라 OFF·수화구 시작) — 첨부 패널에서만 갈리고 상단바는 페이스톡
    onStartCall: (video: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatRoomViewModel = chatRoomViewModel(chatRoomId, groupId)
) {
    val uiState by viewModel.uiState.collectAsState()
    val onAction = viewModel::onAction
    val sg = SgTheme.colors
    val listState = rememberLazyListState()
    var input by rememberSaveable { mutableStateOf("") }
    // + 버튼 첨부 패널(카톡 미러) — 열 때 키보드를 내리고 그 자리에 나타난다
    var showAttachments by rememberSaveable { mutableStateOf(false) }
    // 첨부 패널 안의 이모지 페이지(카톡 미러) — 패널을 새로 열면 첨부 목록으로 되돌아온다
    var showEmojiPicker by rememberSaveable { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    // 허브(세션 VM)에 진입/이탈을 알린다 — 이 방의 미읽음 뱃지를 0으로 만들고 실시간 증가에서 제외
    val hubViewModel = sessionChatViewModel()

    DisposableEffect(chatRoomId) {
        hubViewModel.onAction(ChatViewModel.Action.RoomOpened(chatRoomId))
        onDispose { hubViewModel.onAction(ChatViewModel.Action.RoomClosed(chatRoomId)) }
    }
    val pickImage = rememberImagePickerLauncher { picked ->
        onAction(ChatRoomViewModel.Action.Attach(picked.bytes, picked.fileName, picked.contentType))
    }
    val pickFile = rememberFilePickerLauncher { picked ->
        onAction(ChatRoomViewModel.Action.Attach(picked.bytes, picked.fileName, picked.contentType))
    }
    // "읽음 N" 파생용 — 타인의 읽음 위치만 남긴다(내 위치는 세지 않는다, 웹 미러)
    val otherReadPositions = remember(uiState.readPositions, uiState.myUserId) {
        uiState.readPositions.filterKeys { it != uiState.myUserId }.values.toList()
    }

    // 마지막 표시 인덱스 — 목록은 뒤집혀 그려지므로 맨 아래(최신) = 마지막 인덱스
    fun lastDisplayIndex(): Int = (uiState.messages.size - 1).coerceAtLeast(0)

    LaunchedEffect(Unit) {
        viewModel.event.collect { event ->
            when (event) {
                ChatRoomViewModel.Event.Sent -> {
                    input = ""
                    listState.animateScrollToItem(lastDisplayIndex())
                }
            }
        }
    }
    // 진입 시 맨 아래(최신)로, 이후 새 메시지는 맨 아래 근처를 보고 있을 때만 따라간다(웹 auto-scroll 미러)
    val latestMessageId = uiState.messages.firstOrNull()?.id
    var initialScrolled by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(latestMessageId) {
        if (latestMessageId == null) return@LaunchedEffect
        if (!initialScrolled) {
            initialScrolled = true
            listState.scrollToItem(lastDisplayIndex())
        } else {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0

            if (lastVisible >= listState.layoutInfo.totalItemsCount - 3) {
                listState.animateScrollToItem(lastDisplayIndex())
            }
        }
    }
    // 상단 근처에 닿으면 이전 페이지를 자동 로드한다(무한 스크롤) — 재진입·소진 시 과호출은 VM이
    // 거른다. 초기 하단 정렬 전(진입 직후엔 인덱스 0)에는 발화하지 않는다. 로드로 위에 끼어든
    // 페이지는 키 기반 앵커(첫 가시 메시지)가 같은 프레임에 위치를 유지해 줘 스크롤 보정이 필요 없다
    LaunchedEffect(Unit) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { first ->
                if (initialScrolled && first < 3) onAction(ChatRoomViewModel.Action.LoadOlder)
            }
    }
    // 키보드가 올라와 리스트가 줄어드는 동안, 맨 아래 근처를 보고 있었다면 최신 메시지가 가려지지
    // 않게 바닥에 앵커시킨다(새 메시지 따라가기와 같은 규칙 — 과거 메시지를 읽는 중이면 그대로 둔다)
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    var prevImeBottom by remember { mutableStateOf(0) }

    LaunchedEffect(imeBottom) {
        // 입력창 포커스로 키보드가 "올라올 때"만 첨부 패널을 닫는다(카톡 미러) — 값이 커질 때로
        // 판정해야 한다: 단순 >0 판정이면 +로 패널을 열며 키보드가 내려가는 동안의 잔여 inset에
        // 걸려 패널이 뜨자마자 닫힌다
        if (imeBottom > prevImeBottom) showAttachments = false
        prevImeBottom = imeBottom
        if (imeBottom > 0 && uiState.messages.isNotEmpty()) {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0

            if (lastVisible >= listState.layoutInfo.totalItemsCount - 3) {
                listState.scrollToItem(lastDisplayIndex())
            }
        }
    }
    // 패널이 열려 리스트가 줄어들 때도 키보드와 같은 규칙으로 바닥에 앵커시킨다
    LaunchedEffect(showAttachments) {
        if (showAttachments && uiState.messages.isNotEmpty()) {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0

            if (lastVisible >= listState.layoutInfo.totalItemsCount - 3) {
                listState.scrollToItem(lastDisplayIndex())
            }
        }
    }
    Column(modifier.fillMaxSize().background(sg.paper).imePadding()) {
        SgTopBar(
            title = title,
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = sg.ink)
                }
            },
            actions = {
                // 통화 발신 — 채팅방 세션에 통화가 붙는다(페이스톡 미러). DM=상대 벨울림(웹 D6),
                // 그룹 방=방 멤버 전원 벨울림 팬아웃(진행 중 통화 합류면 서버가 다시 울리지 않는다)
                IconButton(onClick = { onStartCall(true) }) {
                    if (groupId == null) {
                        Icon(Icons.Default.Call, contentDescription = "통화", tint = sg.accent)
                    } else {
                        Icon(Icons.Default.Videocam, contentDescription = "화상회의", tint = sg.accent)
                    }
                }
            }
        )
        // 통화 진행 중 라이브 바(웹 라이브 카드·카톡 진행 중 배너 미러) — 참가는 통화 버튼과
        // 같은 경로다(진행 중 통화 합류는 서버가 다시 울리지 않는다)
        if (uiState.callRoster.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(sg.accentSoft)
                    .padding(start = 16.dp, end = 4.dp)
            ) {
                Icon(Icons.Default.Videocam, contentDescription = null, tint = sg.accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "${uiState.callRoster.joinToString(", ") { it.userName }}님이 통화 중이에요",
                    style = SgTheme.typography.bodySmall,
                    color = sg.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { onStartCall(true) }) {
                    Text("참가", style = SgTheme.typography.labelLarge, color = sg.accent)
                }
            }
        }
        Box(Modifier.weight(1f)) {
            when {
                uiState.messages.isEmpty() && uiState.isLoading ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = sg.accent)
                    }
                uiState.messages.isEmpty() && uiState.error != null ->
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(uiState.error.orEmpty(), style = SgTheme.typography.bodyMedium, color = sg.rust)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { onAction(ChatRoomViewModel.Action.Refresh) }) {
                            Text("다시 시도", color = sg.accent)
                        }
                    }
                uiState.messages.isEmpty() ->
                    SgEmptyState(
                        title = "메시지가 없습니다",
                        subtitle = "첫 메시지를 보내보세요.",
                        icon = Icons.AutoMirrored.Filled.Chat,
                        modifier = Modifier.fillMaxSize()
                    )
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 이전 페이지가 위로 끼어도 위치가 유지되는 건 키 기반 앵커가 첫 가시 "메시지"에
                    // 붙기 때문 — 로딩 표시를 행으로 추가하면(항상 0번) 앵커가 그리로 붙어 로드 후
                    // 최상단으로 튀므로, 로딩 표시는 리스트 밖 오버레이로 그린다
                    // VM 목록은 최신순 — 화면은 뒤집어 오래된 순(위=과거)으로 그린다(웹 reverse 미러)
                    items(
                        count = uiState.messages.size,
                        key = { uiState.messages[uiState.messages.size - 1 - it].id }
                    ) { index ->
                        val messageIndex = uiState.messages.size - 1 - index
                        val message = uiState.messages[messageIndex]
                        // 최신순 목록이라 시간상 직전 메시지는 다음 인덱스
                        val previous = uiState.messages.getOrNull(messageIndex + 1)
                        val isMine = message.userId == uiState.myUserId

                        MessageRow(
                            message = message,
                            isMine = isMine,
                            showAuthor = previous?.userId != message.userId,
                            // "읽음 N" = 내 메시지에 대해, 위치가 그 메시지 이상인 타인 수(웹 미러)
                            readCount = if (isMine) otherReadPositions.count { it >= message.id } else 0
                        )
                    }
                }
            }
            // 이전 페이지 로딩 표시 — 행이 아닌 오버레이(위 주석 참고)
            if (uiState.isLoadingOlder) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp).size(20.dp),
                    strokeWidth = 2.dp,
                    color = sg.accent
                )
            }
        }
        uiState.actionError?.let {
            Text(
                it,
                style = SgTheme.typography.bodySmall,
                color = sg.rust,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        // 입력 중 표시 — 입력바 바로 위의 얇은 띠(웹 미러)
        if (uiState.typists.isNotEmpty()) {
            Text(
                "${uiState.typists.values.joinToString(", ")}님이 입력 중...",
                style = SgTheme.typography.bodySmall,
                color = sg.inkSoft,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        uiState.pendingAttachment?.let { pending ->
            PendingAttachmentChip(
                pending = pending,
                onClear = { onAction(ChatRoomViewModel.Action.ClearAttachment) }
            )
        }
        MessageInputBar(
            value = input,
            onValueChange = {
                input = it
                // 빈 입력은 타이핑 신호를 내지 않는다(웹 미러)
                if (it.isNotBlank()) onAction(ChatRoomViewModel.Action.Typing)
            },
            isSending = uiState.isSending,
            hasPendingAttachment = uiState.pendingAttachment != null,
            attachmentsOpen = showAttachments,
            onToggleAttachments = {
                if (showAttachments) {
                    showAttachments = false
                } else {
                    // 키보드를 내리고 그 자리에 패널을 띄운다(카톡 미러) — 항상 첨부 목록부터
                    focusManager.clearFocus()
                    keyboard?.hide()
                    showEmojiPicker = false
                    showAttachments = true
                }
            },
            onSend = { onAction(ChatRoomViewModel.Action.Send(input)) }
        )
        if (showAttachments) {
            if (showEmojiPicker) {
                // 이모지 선택 — 입력창에 덧붙이고 패널은 유지(연속 선택).
                // TextField 밖에서 넣는 입력이라 타이핑 신호는 직접 낸다
                EmojiPanel(
                    onPick = { emoji ->
                        input += emoji
                        onAction(ChatRoomViewModel.Action.Typing)
                    }
                )
            } else {
                AttachmentPanel(
                    onPickEmoji = { showEmojiPicker = true },
                    onPickImage = { showAttachments = false; pickImage() },
                    onPickFile = { showAttachments = false; pickFile() },
                    onStartCall = { video -> showAttachments = false; onStartCall(video) }
                )
            }
        }
    }
}

/** 채팅방 VM — 백스택 엔트리 스코프(그룹 상세 패턴), 화면을 떠나면 소켓 구독도 함께 정리된다 */
@Composable
private fun chatRoomViewModel(chatRoomId: Long, groupId: Long?): ChatRoomViewModel {
    val container = LocalAppContainer.current

    return viewModel(key = "chat-room-$chatRoomId") {
        ChatRoomViewModel(
            groupId = groupId,
            chatRoomId = chatRoomId,
            getChatMessagesUseCase = container.getChatMessagesUseCase,
            sendChatMessageUseCase = container.sendChatMessageUseCase,
            markChatMessagesReadUseCase = container.markChatMessagesReadUseCase,
            uploadChatFileUseCase = container.uploadChatFileUseCase,
            sendChatTypingUseCase = container.sendChatTypingUseCase,
            getChatReadPositionsUseCase = container.getChatReadPositionsUseCase,
            getCallRosterUseCase = container.getCallRosterUseCase,
            observeChatRoomEventsUseCase = container.observeChatRoomEventsUseCase,
            getCurrentUserIdUseCase = container.getCurrentUserIdUseCase
        )
    }
}

/** 웹 MessageBubble 미러 — 내 메시지는 우측 accent, 타인은 좌측 linen+작성자 변경 시 아바타/이름 */
@Composable
private fun MessageRow(
    message: ChatMessage,
    isMine: Boolean,
    showAuthor: Boolean,
    readCount: Int,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        if (!isMine) {
            if (showAuthor) {
                SgAvatar(message.authorName, size = 32.dp, imageUrl = message.authorProfileImg)
            } else {
                // 같은 작성자 연속 메시지는 아바타 없이 자리만 맞춘다(웹 spacer 미러)
                Spacer(Modifier.width(32.dp))
            }
            Spacer(Modifier.width(8.dp))
        }
        // "읽음 N"은 버블 안쪽 옆·바닥 정렬 — 우측(내) 버블은 좌측에, 좌측(타인) 버블은 우측에
        if (isMine && readCount > 0) {
            ReadCountLabel(readCount, Modifier.align(Alignment.Bottom).padding(end = 4.dp))
        }
        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
        ) {
            if (!isMine && showAuthor) {
                Text(
                    message.authorName,
                    style = SgTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = sg.inkSoft,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
            message.attachment?.let { attachment ->
                if (attachment.isImage) {
                    // 이미지 첨부는 말풍선 배경 없이 그린다(웹 미러)
                    AsyncImage(
                        model = attachment.url,
                        contentDescription = attachment.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(200.dp).clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(sg.linen)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            tint = sg.inkSoft,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            attachment.name ?: "파일",
                            style = SgTheme.typography.bodySmall,
                            color = sg.ink,
                            maxLines = 1
                        )
                    }
                }
                if (message.text.isNotEmpty()) Spacer(Modifier.height(4.dp))
            }
            if (message.text.isNotEmpty()) {
                Text(
                    message.text,
                    style = SgTheme.typography.bodyMedium,
                    color = if (isMine) sg.onAccent else sg.ink,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isMine) sg.accent else sg.linen)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
        if (!isMine && readCount > 0) {
            ReadCountLabel(readCount, Modifier.align(Alignment.Bottom).padding(start = 4.dp))
        }
    }
}

/** 내 메시지의 "읽음 N" — 1명이면 숫자 없이 "읽음"(웹 미러) */
@Composable
private fun ReadCountLabel(readCount: Int, modifier: Modifier = Modifier) {
    Text(
        if (readCount > 1) "읽음 $readCount" else "읽음",
        style = SgTheme.typography.labelSmall,
        color = SgTheme.colors.accent,
        modifier = modifier
    )
}

/** 전송 대기 첨부 칩(웹 pending chip 미러) — 취소하면 업로드 없이 그냥 버려진다 */
@Composable
private fun PendingAttachmentChip(
    pending: ChatRoomViewModel.PendingAttachment,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors

    Row(
        modifier = modifier.fillMaxWidth().background(sg.linen).padding(start = 16.dp, end = 4.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (pending.isImage) Icons.Default.Image else Icons.Default.Description,
            contentDescription = null,
            tint = sg.inkSoft,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "${pending.fileName} (${formatFileSize(pending.bytes.size)})",
            style = SgTheme.typography.bodySmall,
            color = sg.ink,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Close, contentDescription = "첨부 취소", tint = sg.inkSoft, modifier = Modifier.size(16.dp))
        }
    }
}

/** 웹 formatFileSize 미러 — 1KB 미만 B, 1MB 미만 반올림 KB, 이상은 소수 1자리 MB */
private fun formatFileSize(size: Int): String = when {
    size < 1024 -> "${size}B"
    size < 1024 * 1024 -> "${(size.toDouble() / 1024).roundToInt()}KB"
    else -> "${(size.toDouble() / (1024 * 1024) * 10).roundToInt() / 10.0}MB"
}

@Composable
private fun MessageInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    isSending: Boolean,
    hasPendingAttachment: Boolean,
    attachmentsOpen: Boolean,
    onToggleAttachments: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors
    // 첨부가 있으면 본문 없이도 전송 가능(웹 미러)
    val canSend = (value.isNotBlank() || hasPendingAttachment) && !isSending

    Row(
        modifier = modifier.fillMaxWidth().background(sg.linen).padding(horizontal = 8.dp, vertical = 6.dp),
        // iOS HStack(spacing: 8) 미러 — 버튼은 기본 48dp 박스 대신 아이콘에 딱 붙는 크기로
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 첨부 진입점은 +로 모은다(카톡 미러) — 패널이 열려 있으면 닫기(×)로 바뀐다
        IconButton(onClick = onToggleAttachments, enabled = !isSending, modifier = Modifier.size(28.dp)) {
            Icon(
                if (attachmentsOpen) Icons.Default.Close else Icons.Default.Add,
                contentDescription = if (attachmentsOpen) "첨부 닫기" else "첨부",
                tint = sg.inkSoft
            )
        }
        SgTextField(
            value = value,
            onValueChange = onValueChange,
            label = if (hasPendingAttachment) "메시지 (선택)" else null,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onSend, enabled = canSend, modifier = Modifier.size(28.dp)) {
            if (isSending) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = sg.accent)
            } else {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "전송",
                    tint = if (canSend) sg.accent else sg.inkFaint
                )
            }
        }
    }
}

/** + 버튼으로 여는 첨부 패널(카톡 미러) — 키보드 자리에 나타나 이모지/사진/파일/보이스톡/페이스톡을 고른다 */
@Composable
private fun AttachmentPanel(
    onPickEmoji: () -> Unit,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit,
    onStartCall: (video: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().background(SgTheme.colors.linen).padding(vertical = 24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // 패널이 이모지 페이지로 전환된다(닫히지 않음) — 선택은 입력창에 덧붙는다
        AttachmentPanelItem(Icons.Default.EmojiEmotions, "이모지", onPickEmoji)
        AttachmentPanelItem(Icons.Default.Image, "사진", onPickImage)
        AttachmentPanelItem(Icons.Default.AttachFile, "파일", onPickFile)
        // 통화 발신과 같은 경로(카톡 미러) — 보이스톡=카메라 OFF·수화구 시작, 페이스톡=영상 통화
        AttachmentPanelItem(Icons.Default.Call, "보이스톡", { onStartCall(false) })
        AttachmentPanelItem(Icons.Default.Videocam, "페이스톡", { onStartCall(true) })
    }
}

/** 이모지 페이지(카톡 미러) — 첨부 패널의 이모지 항목이 연다. 선택할 때마다 입력창에 덧붙는다(패널 유지) */
@Composable
private fun EmojiPanel(onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(8),
        modifier = modifier.fillMaxWidth().height(220.dp).background(SgTheme.colors.linen),
        contentPadding = PaddingValues(12.dp)
    ) {
        items(CHAT_EMOJIS) { emoji ->
            Text(
                emoji,
                fontSize = 24.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.clickable { onPick(emoji) }.padding(vertical = 8.dp)
            )
        }
    }
}

// 이모지 팔레트 — iosApp ChatRoomView.chatEmojis와 1:1 동일 목록(웹엔 없는 모바일 전용)
private val CHAT_EMOJIS = listOf(
    "😀", "😂", "🤣", "😊", "😍", "😘", "😎", "🤔",
    "😅", "😭", "😢", "😡", "😱", "🥳", "😴", "🤗",
    "👍", "👎", "👏", "🙏", "💪", "🤝", "✌️", "👌",
    "❤️", "💕", "💖", "💔", "🔥", "⭐", "✨", "🎉",
    "🎂", "🎁", "🌸", "🌈", "☀️", "🌙", "☕", "🍺",
    "🍕", "🍗", "🍜", "🍰", "⚽", "🏀", "🎮", "🎵",
    "🚗", "✈️", "🏠", "💻", "📱", "💤", "💯", "🆗"
)

@Composable
private fun AttachmentPanelItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(56.dp).clip(CircleShape).background(sg.paper)
        ) {
            Icon(icon, contentDescription = label, tint = sg.accent)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = SgTheme.typography.labelSmall, color = sg.inkSoft)
    }
}
