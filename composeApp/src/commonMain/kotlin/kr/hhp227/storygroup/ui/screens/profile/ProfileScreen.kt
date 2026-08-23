package kr.hhp227.storygroup.ui.screens.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import kr.hhp227.storygroup.di.sessionViewModel
import kr.hhp227.storygroup.shared.config.AppLinks
import kr.hhp227.storygroup.ui.components.SgAvatar
import kr.hhp227.storygroup.ui.components.SgCard
import kr.hhp227.storygroup.ui.navigation.NavigationAction
import kr.hhp227.storygroup.ui.navigation.sessionNavigationViewModel
import kr.hhp227.storygroup.ui.theme.SgTheme

// 약관·정책 — 웹 /terms·/privacy를 외부 브라우저로 연다(설정 허브 "약관 및 정책" 섹션 미러)
private const val TERMS_URL = AppLinks.TERMS_URL

private const val PRIVACY_POLICY_URL = AppLinks.PRIVACY_URL

/** 프로필 — 내 정보(GET /api/users/me) 헤더 + 메뉴(웹 /settings 허브 대응). VM은 드로어 헤더와 공유하는 세션 스코프 */
@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigationAction: (NavigationAction) -> Unit = sessionNavigationViewModel()::onAction,
    viewModel: ProfileViewModel = sessionViewModel { ProfileViewModel(it.getMyProfileUseCase) }
) {
    val uiState by viewModel.uiState.collectAsState()
    val profile = uiState.profile
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SgCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SgAvatar(profile?.name ?: "?", size = 64.dp, imageUrl = profile?.profileImg)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        profile?.name ?: "불러오는 중...",
                        style = SgTheme.typography.titleLarge,
                        color = SgTheme.colors.ink
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(profile?.email ?: "", style = SgTheme.typography.bodyMedium, color = SgTheme.colors.inkSoft)
                    if (profile?.statusMessage != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            profile.statusMessage!!,
                            style = SgTheme.typography.bodySmall,
                            color = SgTheme.colors.inkFaint
                        )
                    }
                }
            }
        }
        SgCard(modifier = Modifier.fillMaxWidth()) {
            ProfileMenuRow(
                icon = Icons.Default.ManageAccounts,
                label = "계정 설정",
                onClick = { onNavigationAction(NavigationAction.NavigateToAccountSettings) }
            )
            Divider(color = SgTheme.colors.stoneBorder, modifier = Modifier.padding(horizontal = 16.dp))
            ProfileMenuRow(
                icon = Icons.Default.Settings,
                label = "앱 설정",
                onClick = { onNavigationAction(NavigationAction.NavigateToAppSettings) }
            )
            Divider(color = SgTheme.colors.stoneBorder, modifier = Modifier.padding(horizontal = 16.dp))
            // 개인정보 보호 — 웹 설정 허브 /settings/blocked 미러
            ProfileMenuRow(
                icon = Icons.Default.Block,
                label = "차단 사용자 관리",
                onClick = { onNavigationAction(NavigationAction.NavigateToBlockedUsers) }
            )
            Divider(color = SgTheme.colors.stoneBorder, modifier = Modifier.padding(horizontal = 16.dp))
            ProfileMenuRow(
                icon = Icons.Default.Description,
                label = "이용약관",
                onClick = { uriHandler.openUri(TERMS_URL) }
            )
            Divider(color = SgTheme.colors.stoneBorder, modifier = Modifier.padding(horizontal = 16.dp))
            ProfileMenuRow(
                icon = Icons.Default.PrivacyTip,
                label = "개인정보처리방침",
                onClick = { uriHandler.openUri(PRIVACY_POLICY_URL) }
            )
            Divider(color = SgTheme.colors.stoneBorder, modifier = Modifier.padding(horizontal = 16.dp))
            ProfileMenuRow(
                icon = Icons.AutoMirrored.Filled.Logout,
                label = "로그아웃",
                onClick = onLogout,
                tint = SgTheme.colors.rust
            )
        }
    }
}

@Composable
private fun ProfileMenuRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = tint ?: SgTheme.colors.inkSoft, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = SgTheme.typography.bodyLarge, color = tint ?: SgTheme.colors.ink)
    }
}
