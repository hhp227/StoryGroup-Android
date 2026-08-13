package kr.hhp227.storygroup.ui.screens.friend

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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kr.hhp227.storygroup.di.sessionViewModel
import kr.hhp227.storygroup.shared.domain.model.Friend
import kr.hhp227.storygroup.shared.domain.model.UserSearchResult
import kr.hhp227.storygroup.ui.components.SgAvatar
import kr.hhp227.storygroup.ui.components.SgCard
import kr.hhp227.storygroup.ui.components.SgEmptyState
import kr.hhp227.storygroup.ui.theme.SgTheme

/** 세션 스코프 친구 VM — 로그아웃 시 함께 사라진다(다음 로그인은 init이 자가 로드) */
@Composable
internal fun sessionFriendsViewModel(): FriendsViewModel = sessionViewModel {
    FriendsViewModel(
        getFriendsUseCase = it.getFriendsUseCase,
        addFriendUseCase = it.addFriendUseCase,
        removeFriendUseCase = it.removeFriendUseCase,
        searchUsersUseCase = it.searchUsersUseCase,
        openDirectRoomUseCase = it.openDirectRoomUseCase,
        observePersonalEventsUseCase = it.observePersonalEventsUseCase
    )
}

/**
 * 친구 탭 — 웹 /search 미러(검색 전=친구 목록, 검색 후=사용자 검색 결과, 카카오톡 친구 탭 패턴).
 * 검색바는 콘텐츠 상단 인라인(웹의 페이지 내 검색 폼 미러 — 상단바는 쉘 소유라 제목 자리가 없다).
 * 행 탭은 무동작(공개 프로필 화면은 범위 제외) — 액션은 메시지(DM)/해제 버튼 2개.
 * iosApp FriendsView.swift와 1:1 미러
 */
@Composable
fun FriendsScreen(
    onOpenChatRoom: (chatRoomId: Long, groupId: Long?, title: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FriendsViewModel = sessionFriendsViewModel()
) {
    FriendsContent(viewModel = viewModel, onOpenChatRoom = onOpenChatRoom, modifier = modifier)
}

@Composable
private fun FriendsContent(
    viewModel: FriendsViewModel,
    onOpenChatRoom: (chatRoomId: Long, groupId: Long?, title: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val onAction = viewModel::onAction
    val sg = SgTheme.colors
    var queryText by rememberSaveable { mutableStateOf("") }
    // 해제 확인 다이얼로그 대상 — 실수 탭 방지(웹은 즉시 해제지만 모바일은 확인을 거친다)
    var removeTarget by remember { mutableStateOf<Friend?>(null) }

    LaunchedEffect(Unit) {
        viewModel.event.collect { event ->
            when (event) {
                is FriendsViewModel.Event.DmOpened -> onOpenChatRoom(event.chatRoomId, null, event.title)
            }
        }
    }
    Column(modifier.fillMaxSize().background(sg.paper)) {
        SearchBar(
            queryText = queryText,
            onQueryChange = {
                queryText = it
                // 비우면 즉시 친구 목록 복귀(웹의 "복귀 불가"는 미러하지 않는다)
                if (it.isEmpty()) onAction(FriendsViewModel.Action.Search(""))
            },
            onSearch = { onAction(FriendsViewModel.Action.Search(queryText)) },
            modifier = Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)
        )
        uiState.actionError?.let {
            Text(
                it,
                style = SgTheme.typography.bodySmall,
                color = sg.rust,
                modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp)
            )
        }
        uiState.searchError?.let {
            Text(
                it,
                style = SgTheme.typography.bodySmall,
                color = sg.rust,
                modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp)
            )
        }
        val searchResults = uiState.searchResults

        when {
            uiState.isSearching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = sg.accent)
            }
            // 검색 후 — 사용자 검색 결과(친구 추가/해제 토글)가 친구 목록 자리를 대체한다
            searchResults != null -> SearchResultList(
                results = searchResults,
                friendIds = uiState.friendIds,
                processingUserId = uiState.processingUserId,
                onAddFriend = { onAction(FriendsViewModel.Action.AddFriend(it)) },
                onRemoveFriend = { onAction(FriendsViewModel.Action.RemoveFriend(it.id)) }
            )
            // 검색 전 — 친구 목록 기본 화면
            uiState.isLoading && uiState.friends.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = sg.accent)
                }
            uiState.error != null && uiState.friends.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize().padding(vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(uiState.error.orEmpty(), style = SgTheme.typography.bodyMedium, color = sg.rust)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { onAction(FriendsViewModel.Action.Refresh) }) {
                    Text("다시 시도", color = sg.accent)
                }
            }
            uiState.friends.isEmpty() -> SgEmptyState(
                title = "아직 친구가 없습니다",
                subtitle = "위 검색으로 사용자를 찾아 친구를 추가해보세요.",
                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp)
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.friends, key = { it.userId }) { friend ->
                    FriendRow(
                        friend = friend,
                        isBusy = uiState.isOpeningDm || uiState.processingUserId != null,
                        onOpenDm = { onAction(FriendsViewModel.Action.OpenDm(friend.userId, friend.name)) },
                        onRemove = { removeTarget = friend }
                    )
                }
            }
        }
    }
    removeTarget?.let { friend ->
        RemoveFriendDialog(
            friend = friend,
            onDismiss = { removeTarget = null },
            onConfirm = {
                removeTarget = null
                onAction(FriendsViewModel.Action.RemoveFriend(friend.userId))
            }
        )
    }
}

/** 인라인 검색바 — 실행은 IME 검색(제출 기반), 지우기 아이콘은 즉시 친구 목록 복귀 */
@Composable
private fun SearchBar(
    queryText: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(sg.linen, SgTheme.shapes.button)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, contentDescription = "검색", tint = sg.inkFaint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = queryText,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            textStyle = SgTheme.typography.bodyMedium.copy(color = sg.ink),
            singleLine = true,
            cursorBrush = SolidColor(sg.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            decorationBox = { innerTextField ->
                Box {
                    if (queryText.isEmpty()) {
                        Text("이름으로 사용자 검색", style = SgTheme.typography.bodyMedium, color = sg.inkFaint)
                    }
                    innerTextField()
                }
            }
        )
        if (queryText.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Default.Close, contentDescription = "지우기", tint = sg.inkFaint)
            }
        }
    }
}

/** 검색 결과 목록 — 같은 그룹 소속 사용자만 나온다(서버 필터, 본인·차단 제외) */
@Composable
private fun SearchResultList(
    results: List<UserSearchResult>,
    friendIds: Set<Long>,
    processingUserId: Long?,
    onAddFriend: (UserSearchResult) -> Unit,
    onRemoveFriend: (UserSearchResult) -> Unit,
    modifier: Modifier = Modifier
) {
    if (results.isEmpty()) {
        SgEmptyState(
            title = "검색 결과가 없습니다",
            subtitle = "같은 그룹에 소속된 사용자만 검색됩니다.",
            modifier = modifier.fillMaxWidth().padding(vertical = 48.dp)
        )
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(results, key = { it.id }) { user ->
            SearchResultRow(
                user = user,
                isFriend = user.id in friendIds,
                isProcessing = processingUserId == user.id,
                enabled = processingUserId == null,
                onAddFriend = { onAddFriend(user) },
                onRemoveFriend = { onRemoveFriend(user) }
            )
        }
    }
}

@Composable
private fun SearchResultRow(
    user: UserSearchResult,
    isFriend: Boolean,
    isProcessing: Boolean,
    enabled: Boolean,
    onAddFriend: () -> Unit,
    onRemoveFriend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors

    SgCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SgAvatar(user.name, imageUrl = user.profileImg)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(user.name, style = SgTheme.typography.titleSmall, color = sg.ink)
                if (!user.statusMessage.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(user.statusMessage.orEmpty(), style = SgTheme.typography.bodySmall, color = sg.inkFaint)
                }
            }
            Spacer(Modifier.width(8.dp))
            if (isProcessing) {
                CircularProgressIndicator(color = sg.accent, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else if (isFriend) {
                OutlinedButton(
                    onClick = onRemoveFriend,
                    enabled = enabled,
                    shape = SgTheme.shapes.button,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = sg.inkSoft)
                ) {
                    Text("친구 해제", style = SgTheme.typography.labelLarge)
                }
            } else {
                Button(
                    onClick = onAddFriend,
                    enabled = enabled,
                    shape = SgTheme.shapes.button,
                    colors = ButtonDefaults.buttonColors(backgroundColor = sg.accent, contentColor = sg.onAccent)
                ) {
                    Text("친구 추가", style = SgTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** 친구 행 — 웹 친구 카드 미러(이름+상태메시지, 메시지/해제 버튼 2개). 행 탭은 무동작 */
@Composable
private fun FriendRow(
    friend: Friend,
    isBusy: Boolean,
    onOpenDm: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors

    SgCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                SgAvatar(friend.name, imageUrl = friend.profileImg)
                // 온라인 도트 — 친구 탭 전용이라 공용 SgAvatar는 건드리지 않는다(화면 로컬 오버레이)
                if (friend.online) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(14.dp)
                            .background(sg.linen, CircleShape)
                            .padding(2.dp)
                            .background(Color(0xFF34C759), CircleShape)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(friend.name, style = SgTheme.typography.titleSmall, color = sg.ink)
                if (!friend.statusMessage.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(friend.statusMessage.orEmpty(), style = SgTheme.typography.bodySmall, color = sg.inkFaint)
                }
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onOpenDm, enabled = !isBusy) {
                Text("메시지", style = SgTheme.typography.labelLarge, color = sg.accent)
            }
            TextButton(onClick = onRemove, enabled = !isBusy) {
                Text("해제", style = SgTheme.typography.labelLarge, color = sg.inkSoft)
            }
        }
    }
}

/** 해제 확인 다이얼로그 — 단방향 등록이라 상대에게 알림·영향 없음(즐겨찾기 해제 성격) */
@Composable
private fun RemoveFriendDialog(
    friend: Friend,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val sg = SgTheme.colors

    Dialog(onDismissRequest = onDismiss) {
        SgCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "친구 해제",
                    style = SgTheme.typography.titleMedium,
                    color = sg.ink,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${friend.name}님을 친구에서 해제할까요?",
                    style = SgTheme.typography.bodyMedium,
                    color = sg.inkSoft
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("취소", color = sg.inkSoft)
                    }
                    TextButton(onClick = onConfirm) {
                        Text("해제", color = sg.rust)
                    }
                }
            }
        }
    }
}
