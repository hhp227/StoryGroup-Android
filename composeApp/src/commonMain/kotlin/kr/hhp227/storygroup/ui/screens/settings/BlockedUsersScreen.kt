package kr.hhp227.storygroup.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedButton
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.hhp227.storygroup.di.LocalAppContainer
import kr.hhp227.storygroup.ui.components.SgAvatar
import kr.hhp227.storygroup.ui.components.SgCard
import kr.hhp227.storygroup.ui.components.SgEmptyState
import kr.hhp227.storygroup.ui.components.SgTopBar
import kr.hhp227.storygroup.ui.navigation.NavigationAction
import kr.hhp227.storygroup.ui.navigation.sessionNavigationViewModel
import kr.hhp227.storygroup.ui.theme.SgTheme
import kr.hhp227.storygroup.ui.util.formatJoinDate

/** 백스택 엔트리 스코프 VM — 화면이 default parameter로 선언(GroupDetail 패턴) */
@Composable
private fun blockedUsersViewModel(): BlockedUsersViewModel {
    val container = LocalAppContainer.current

    return viewModel {
        BlockedUsersViewModel(
            getBlockedUsersUseCase = container.getBlockedUsersUseCase,
            unblockUserUseCase = container.unblockUserUseCase
        )
    }
}

/**
 * 차단 사용자 관리 — 웹 /settings/blocked 미러(아바타+이름+차단일+해제 버튼).
 * 진입점은 셸 프로필 탭 메뉴. iosApp BlockedUsersView.swift와 1:1 미러
 */
@Composable
fun BlockedUsersScreen(
    modifier: Modifier = Modifier,
    onNavigationAction: (NavigationAction) -> Unit = sessionNavigationViewModel()::onAction,
    viewModel: BlockedUsersViewModel = blockedUsersViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val onAction = viewModel::onAction
    val sg = SgTheme.colors

    Column(modifier.fillMaxSize()) {
        SgTopBar(
            title = "차단 사용자 관리",
            navigationIcon = {
                IconButton(onClick = { onNavigationAction(NavigationAction.NavigateBack) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                }
            }
        )
        val blocked = uiState.blocked

        when {
            blocked == null && uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = sg.accent)
            }
            blocked == null -> Column(
                modifier = Modifier.fillMaxSize().padding(vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(uiState.loadError ?: "차단 목록을 불러오지 못했습니다.", style = SgTheme.typography.bodyMedium, color = sg.rust)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { onAction(BlockedUsersViewModel.Action.Refresh) }) {
                    Text("다시 시도", color = sg.accent)
                }
            }
            blocked.isEmpty() -> SgEmptyState(
                title = "차단한 사용자가 없습니다.",
                subtitle = "차단하면 그 사용자의 게시글·댓글·채팅이 내 화면에서 숨겨지고, 서로 DM을 보낼 수 없습니다.",
                modifier = Modifier.fillMaxSize()
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.actionError?.let { error ->
                    item(key = "action-error") {
                        Text(error, style = SgTheme.typography.bodySmall, color = sg.rust)
                    }
                }
                items(blocked, key = { it.userId }) { user ->
                    SgCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SgAvatar(user.name, size = 44.dp, imageUrl = user.profileImg)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    user.name,
                                    style = SgTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = sg.ink
                                )
                                Text(
                                    "${formatJoinDate(user.blockedAt)} 차단",
                                    style = SgTheme.typography.labelSmall,
                                    color = sg.inkFaint
                                )
                            }
                            OutlinedButton(
                                onClick = { onAction(BlockedUsersViewModel.Action.Unblock(user.userId)) },
                                enabled = uiState.busyUserId == null,
                                shape = SgTheme.shapes.button,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = sg.inkSoft)
                            ) {
                                Text("차단 해제", style = SgTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}
