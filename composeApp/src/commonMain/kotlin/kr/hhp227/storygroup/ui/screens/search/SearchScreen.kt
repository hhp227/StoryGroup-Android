package kr.hhp227.storygroup.ui.screens.search

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.hhp227.storygroup.di.LocalAppContainer
import kr.hhp227.storygroup.shared.domain.model.FileSearchHit
import kr.hhp227.storygroup.shared.domain.model.GroupSearchHit
import kr.hhp227.storygroup.shared.domain.model.MessageSearchHit
import kr.hhp227.storygroup.shared.domain.model.PostSearchHit
import kr.hhp227.storygroup.shared.domain.model.SearchResults
import kr.hhp227.storygroup.shared.domain.model.UserSearchResult
import kr.hhp227.storygroup.ui.components.SgAvatar
import kr.hhp227.storygroup.ui.components.SgCard
import kr.hhp227.storygroup.ui.components.SgEmptyState
import kr.hhp227.storygroup.ui.components.SgSectionTitle
import kr.hhp227.storygroup.ui.components.SgTopBar
import kr.hhp227.storygroup.ui.theme.SgTheme
import kr.hhp227.storygroup.ui.util.formatRelativeTime

/** 백스택 엔트리 스코프 VM — 화면이 default parameter로 선언(GroupDetail 패턴) */
@Composable
private fun searchViewModel(): SearchViewModel {
    val container = LocalAppContainer.current

    return viewModel {
        SearchViewModel(
            searchUseCase = container.searchUseCase,
            getFriendsUseCase = container.getFriendsUseCase,
            addFriendUseCase = container.addFriendUseCase,
            removeFriendUseCase = container.removeFriendUseCase
        )
    }
}

/**
 * 홈 통합검색 — 웹 /search 미러(5섹션 원페이지, 제출 기반).
 * 검색 전=빈 상태 안내(웹의 친구 목록은 친구 탭 몫이라 미러하지 않는다 — 스펙 확정).
 * 결과 탭: 그룹→상세, 게시글→상세, 파일→URL 브라우저, 메시지→채팅방, 사용자=친구 추가/해제 버튼만.
 * iosApp SearchView.swift와 1:1 미러
 */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenGroupDetail: (groupId: Long) -> Unit,
    onOpenPostDetail: (groupId: Long, postId: Long) -> Unit,
    onOpenChatRoom: (chatRoomId: Long, groupId: Long?, title: String) -> Unit,
    onOpenUserProfile: (userId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = searchViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val onAction = viewModel::onAction
    val sg = SgTheme.colors
    var queryText by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val uriHandler = LocalUriHandler.current

    Column(modifier.fillMaxSize().background(sg.paper)) {
        SgTopBar(
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                }
            }
        ) {
            SearchField(
                queryText = queryText,
                onQueryChange = {
                    queryText = it
                    // 비우면 즉시 초기 화면 복귀(친구 탭 미러)
                    if (it.isEmpty()) onAction(SearchViewModel.Action.ClearResults)
                },
                onSearch = { onAction(SearchViewModel.Action.Search(queryText)) },
                focusRequester = focusRequester
            )
        }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        uiState.actionError?.let {
            Text(
                it,
                style = SgTheme.typography.bodySmall,
                color = sg.rust,
                modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp)
            )
        }
        uiState.error?.let {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(it, style = SgTheme.typography.bodySmall, color = sg.rust, modifier = Modifier.weight(1f))
                TextButton(onClick = { onAction(SearchViewModel.Action.Search(queryText)) }) {
                    Text("다시 시도", color = sg.accent)
                }
            }
        }
        val results = uiState.results

        when {
            uiState.isSearching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = sg.accent)
            }
            results != null -> ResultList(
                results = results,
                friendIds = uiState.friendIds,
                processingUserId = uiState.processingUserId,
                onOpenGroupDetail = onOpenGroupDetail,
                onOpenPostDetail = onOpenPostDetail,
                onOpenChatRoom = onOpenChatRoom,
                onOpenUserProfile = onOpenUserProfile,
                onOpenUrl = { uriHandler.openUri(it) },
                onAddFriend = { onAction(SearchViewModel.Action.AddFriend(it)) },
                onRemoveFriend = { onAction(SearchViewModel.Action.RemoveFriend(it)) }
            )
            else -> SgEmptyState(
                title = "무엇이든 찾아보세요",
                subtitle = "그룹, 게시글, 파일, 메시지, 사용자를 검색할 수 있습니다.",
                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp)
            )
        }
    }
}

/** 상단바 제목 슬롯 검색 필드 — 친구 탭 SearchBar 스타일 미러(paper 캡슐(린넨 상단바 위)+IME 검색) */
@Composable
private fun SearchField(
    queryText: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(sg.paper, SgTheme.shapes.button)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = queryText,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
            textStyle = SgTheme.typography.bodyMedium.copy(color = sg.ink),
            singleLine = true,
            cursorBrush = SolidColor(sg.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            decorationBox = { innerTextField ->
                Box {
                    if (queryText.isEmpty()) {
                        Text("그룹, 게시글, 파일, 메시지 검색", style = SgTheme.typography.bodyMedium, color = sg.inkFaint)
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

/** 5섹션 결과 — 순서는 웹 /search 미러(사용자→그룹→게시글→파일→메시지), 빈 섹션 숨김 */
@Composable
private fun ResultList(
    results: SearchResults,
    friendIds: Set<Long>,
    processingUserId: Long?,
    onOpenGroupDetail: (Long) -> Unit,
    onOpenPostDetail: (Long, Long) -> Unit,
    onOpenChatRoom: (Long, Long?, String) -> Unit,
    onOpenUserProfile: (Long) -> Unit,
    onOpenUrl: (String) -> Unit,
    onAddFriend: (UserSearchResult) -> Unit,
    onRemoveFriend: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (results.isEmpty) {
        SgEmptyState(
            title = "검색 결과가 없습니다",
            subtitle = "다른 검색어로 다시 시도해보세요.",
            modifier = modifier.fillMaxWidth().padding(vertical = 48.dp)
        )
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (results.users.isNotEmpty()) {
            item(key = "users-header") { SgSectionTitle("사용자") }
            items(results.users, key = { "user-${it.id}" }) { user ->
                UserRow(
                    user = user,
                    isFriend = user.id in friendIds,
                    isProcessing = processingUserId == user.id,
                    enabled = processingUserId == null,
                    onAddFriend = { onAddFriend(user) },
                    onRemoveFriend = { onRemoveFriend(user.id) },
                    onOpenProfile = { onOpenUserProfile(user.id) }
                )
            }
        }
        if (results.groups.isNotEmpty()) {
            item(key = "groups-header") { SgSectionTitle("그룹") }
            items(results.groups, key = { "group-${it.id}" }) { group ->
                GroupRow(group = group, onClick = { onOpenGroupDetail(group.id) })
            }
        }
        if (results.posts.isNotEmpty()) {
            item(key = "posts-header") { SgSectionTitle("게시글") }
            items(results.posts, key = { "post-${it.id}" }) { post ->
                PostRow(post = post, onClick = { onOpenPostDetail(post.groupId, post.id) })
            }
        }
        if (results.files.isNotEmpty()) {
            item(key = "files-header") { SgSectionTitle("파일") }
            items(results.files, key = { "file-${it.id}" }) { file ->
                FileRow(file = file, onClick = { onOpenUrl(file.url) })
            }
        }
        if (results.messages.isNotEmpty()) {
            item(key = "messages-header") { SgSectionTitle("메시지") }
            items(results.messages, key = { "message-${it.id}" }) { message ->
                MessageRow(
                    message = message,
                    // DM은 방 이름이 없어 작성자 이름 폴백(알려진 한계 — 스펙 참조)
                    onClick = { onOpenChatRoom(message.chatRoomId, message.groupId, message.groupName ?: message.authorName) }
                )
            }
        }
    }
}

/** 사용자 행 — 친구 탭 SearchResultRow 미러(행 탭=공개 프로필(버튼 영역은 버튼이 우선), 친구 추가/해제 버튼) */
@Composable
private fun UserRow(
    user: UserSearchResult,
    isFriend: Boolean,
    isProcessing: Boolean,
    enabled: Boolean,
    onAddFriend: () -> Unit,
    onRemoveFriend: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors

    SgCard(modifier = modifier.fillMaxWidth(), onClick = onOpenProfile) {
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

@Composable
private fun GroupRow(group: GroupSearchHit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val sg = SgTheme.colors

    SgCard(modifier = modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SgAvatar(group.name, imageUrl = group.image)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(group.name, style = SgTheme.typography.titleSmall, color = sg.ink)
                if (!group.description.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        group.description.orEmpty(),
                        style = SgTheme.typography.bodySmall,
                        color = sg.inkFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun PostRow(post: PostSearchHit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val sg = SgTheme.colors

    SgCard(modifier = modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                post.text,
                style = SgTheme.typography.bodyMedium,
                color = sg.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${post.groupName} · ${post.authorName} · ${formatRelativeTime(post.createdAt)}",
                style = SgTheme.typography.bodySmall,
                color = sg.inkFaint
            )
        }
    }
}

/** 파일 행 — KMP에 그룹 파일 화면이 없어 탭하면 URL을 플랫폼 브라우저로 연다(스펙 확정) */
@Composable
private fun FileRow(file: FileSearchHit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val sg = SgTheme.colors

    SgCard(modifier = modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                file.name,
                style = SgTheme.typography.titleSmall,
                color = sg.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${file.groupName} · ${formatRelativeTime(file.createdAt)}",
                style = SgTheme.typography.bodySmall,
                color = sg.inkFaint
            )
        }
    }
}

@Composable
private fun MessageRow(message: MessageSearchHit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val sg = SgTheme.colors

    SgCard(modifier = modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                message.text,
                style = SgTheme.typography.bodyMedium,
                color = sg.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${message.authorName} · ${message.groupName ?: "DM"} · ${formatRelativeTime(message.createdAt)}",
                style = SgTheme.typography.bodySmall,
                color = sg.inkFaint
            )
        }
    }
}
