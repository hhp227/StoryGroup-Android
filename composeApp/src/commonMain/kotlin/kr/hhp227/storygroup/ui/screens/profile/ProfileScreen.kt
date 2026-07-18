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
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kr.hhp227.storygroup.shared.domain.model.Profile
import kr.hhp227.storygroup.ui.components.SgAvatar
import kr.hhp227.storygroup.ui.components.SgCard
import kr.hhp227.storygroup.ui.theme.SgTheme

/** 프로필 — 내 정보(GET /api/users/me) 헤더 + 메뉴 */
@Composable
fun ProfileScreen(
    profile: Profile?,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors

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
                SgAvatar(profile?.name ?: "?", size = 64.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        profile?.name ?: "불러오는 중...",
                        style = SgTheme.typography.titleLarge,
                        color = sg.ink
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(profile?.email ?: "", style = SgTheme.typography.bodyMedium, color = sg.inkSoft)
                    if (profile?.statusMessage != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            profile.statusMessage!!,
                            style = SgTheme.typography.bodySmall,
                            color = sg.inkFaint
                        )
                    }
                }
            }
        }

        SgCard(modifier = Modifier.fillMaxWidth()) {
            ProfileMenuRow(
                icon = Icons.Default.ManageAccounts,
                label = "계정 설정",
                onClick = { /* TODO: 프로필 편집/비밀번호 변경 */ }
            )
            Divider(color = sg.stoneBorder, modifier = Modifier.padding(horizontal = 16.dp))
            ProfileMenuRow(
                icon = Icons.Default.Settings,
                label = "앱 설정",
                onClick = onOpenSettings
            )
            Divider(color = sg.stoneBorder, modifier = Modifier.padding(horizontal = 16.dp))
            ProfileMenuRow(
                icon = Icons.AutoMirrored.Filled.Logout,
                label = "로그아웃",
                onClick = onLogout,
                tint = sg.rust
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
    val sg = SgTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = tint ?: sg.inkSoft, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = SgTheme.typography.bodyLarge, color = tint ?: sg.ink)
    }
}
