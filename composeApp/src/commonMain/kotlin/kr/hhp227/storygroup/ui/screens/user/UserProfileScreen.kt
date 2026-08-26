package kr.hhp227.storygroup.ui.screens.user

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kr.hhp227.storygroup.di.screenViewModel
import kr.hhp227.storygroup.ui.components.SgAvatar
import kr.hhp227.storygroup.ui.components.SgCard
import kr.hhp227.storygroup.ui.navigation.NavigationAction
import kr.hhp227.storygroup.ui.navigation.sessionNavigationViewModel
import kr.hhp227.storygroup.ui.theme.SgTheme
import kr.hhp227.storygroup.ui.util.formatJoinDate

/**
 * 공개 프로필 — 웹 /users/[userId] 미러(아바타+이름+상태메시지+가입일+bio,
 * 본인=프로필 수정 이동, 타인=1:1 DM+친구 추가/해제). 신고·차단은 여기 없다(게시글 더보기 몫).
 * 풀스크린이 아니라 NavHost dialog 목적지에 뜨는 카드 — iosApp UserProfileView.swift(시트)와 1:1 미러
 */
@Composable
fun UserProfileScreen(
    userId: Long,
    /** 바로 아래가 채팅방이면 그 방 id — 같은 방으로 가려 할 때 또 쌓지 않기 위한 판정 재료 */
    underlyingChatRoomId: Long?,
    modifier: Modifier = Modifier,
    onNavigationAction: (NavigationAction) -> Unit = sessionNavigationViewModel()::onAction,
    // 백스택 엔트리 스코프 VM — 화면이 default parameter로 선언(GroupDetail 패턴)
    viewModel: UserProfileViewModel = screenViewModel(key = "user-profile-$userId") {
        UserProfileViewModel(
            userId = userId,
            getPublicProfileUseCase = it.getPublicProfileUseCase,
            getFriendsUseCase = it.getFriendsUseCase,
            addFriendUseCase = it.addFriendUseCase,
            removeFriendUseCase = it.removeFriendUseCase,
            openDirectRoomUseCase = it.openDirectRoomUseCase,
            getCurrentUserIdUseCase = it.getCurrentUserIdUseCase
        )
    }
) {
    val uiState by viewModel.uiState.collectAsState()
    val onAction = viewModel::onAction
    val sg = SgTheme.colors

    LaunchedEffect(Unit) {
        viewModel.event.collect { event ->
            when (event) {
                is UserProfileViewModel.Event.DmOpened -> onNavigationAction(
                    NavigationAction.OpenChatRoomFromProfile(
                        chatRoomId = event.chatRoomId,
                        groupId = null,
                        title = event.title,
                        underlyingChatRoomId = underlyingChatRoomId
                    )
                )
            }
        }
    }
    SgCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "프로필",
                style = SgTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = sg.ink,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { onNavigationAction(NavigationAction.NavigateBack) }) {
                Icon(Icons.Default.Close, contentDescription = "닫기", tint = sg.inkSoft)
            }
        }
        val profile = uiState.profile

        when {
            profile == null && uiState.isLoading -> Box(
                Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = sg.accent)
            }
            profile == null -> Column(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(uiState.loadError ?: "프로필을 불러오지 못했습니다.", style = SgTheme.typography.bodyMedium, color = sg.rust)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { onAction(UserProfileViewModel.Action.Refresh) }) {
                    Text("다시 시도", color = sg.accent)
                }
            }
            // 다이얼로그 높이는 내용만큼 — 긴 bio만 화면 높이 안에서 스크롤(weight fill=false)
            else -> Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SgAvatar(profile.name, size = 72.dp, imageUrl = profile.profileImg)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(profile.name, style = SgTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = sg.ink)
                        if (!profile.statusMessage.isNullOrBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(profile.statusMessage.orEmpty(), style = SgTheme.typography.bodySmall, color = sg.inkSoft)
                        }
                        Spacer(Modifier.height(2.dp))
                        Text("${formatJoinDate(profile.createdAt)} 가입", style = SgTheme.typography.labelSmall, color = sg.inkFaint)
                    }
                }
                if (!profile.bio.isNullOrBlank()) {
                    Divider(color = sg.stoneBorder)
                    Text(profile.bio.orEmpty(), style = SgTheme.typography.bodyMedium, color = sg.ink)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (uiState.isSelf) {
                        OutlinedButton(
                            onClick = { onNavigationAction(NavigationAction.NavigateToAccountSettings) },
                            shape = SgTheme.shapes.button,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = sg.inkSoft)
                        ) {
                            Text("프로필 수정", style = SgTheme.typography.labelLarge)
                        }
                    } else {
                        Button(
                            onClick = { onAction(UserProfileViewModel.Action.OpenDm) },
                            enabled = !uiState.isOpeningDm,
                            shape = SgTheme.shapes.button,
                            colors = ButtonDefaults.buttonColors(backgroundColor = sg.accent, contentColor = sg.onAccent)
                        ) {
                            Text("1:1 DM", style = SgTheme.typography.labelLarge)
                        }
                        // 친구 여부 판정 불가(목록 로드 실패)면 버튼을 숨긴다 — 웹 isFriend===null 미러
                        uiState.isFriend?.let { isFriend ->
                            OutlinedButton(
                                onClick = { onAction(UserProfileViewModel.Action.ToggleFriend) },
                                enabled = !uiState.isBusy,
                                shape = SgTheme.shapes.button,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = sg.inkSoft)
                            ) {
                                Text(if (isFriend) "친구 해제" else "친구 추가", style = SgTheme.typography.labelLarge)
                            }
                        }
                    }
                }
                uiState.actionError?.let {
                    Text(it, style = SgTheme.typography.bodySmall, color = sg.rust)
                }
            }
        }
    }
}
