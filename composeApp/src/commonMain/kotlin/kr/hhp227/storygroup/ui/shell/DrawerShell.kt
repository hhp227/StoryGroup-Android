package kr.hhp227.storygroup.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.DrawerValue
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.ModalDrawer
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.shared.domain.model.Profile
import kr.hhp227.storygroup.ui.components.SgAvatar
import kr.hhp227.storygroup.ui.components.SgTopBar
import kr.hhp227.storygroup.ui.screens.home.HomeViewModel
import kr.hhp227.storygroup.ui.theme.SgTheme

/** 레거시 쉘: 구 앱 드로어(프로필 헤더 + 라운지·그룹·친구·채팅 + 알림·설정·로그아웃 보강) */
@Composable
internal fun DrawerShell(
    currentDestination: MainDestination,
    onDestinationSelected: (MainDestination) -> Unit,
    profile: Profile?,
    homeViewModel: HomeViewModel,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit
) {
    val sg = SgTheme.colors
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalDrawer(
        drawerState = drawerState,
        drawerBackgroundColor = sg.linen,
        drawerContent = {
            DrawerHeader(profile)
            MainDestination.entries.forEach { destination ->
                DrawerItem(
                    label = destination.label,
                    icon = destination.icon,
                    selected = destination == currentDestination,
                    onClick = {
                        onDestinationSelected(destination)
                        scope.launch { drawerState.close() }
                    }
                )
            }
            Divider(
                color = sg.stoneBorder,
                modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp)
            )
            DrawerItem(
                label = "설정",
                icon = Icons.Default.Settings,
                selected = false,
                onClick = {
                    scope.launch { drawerState.close() }
                    onOpenSettings()
                }
            )
            DrawerItem(
                label = "로그아웃",
                icon = Icons.AutoMirrored.Filled.Logout,
                selected = false,
                onClick = {
                    scope.launch { drawerState.close() }
                    onLogout()
                }
            )
        }
    ) {
        Scaffold(
            backgroundColor = sg.paper,
            topBar = {
                // 홈은 화면이 콜랩싱 상단바를 직접 그린다(메뉴 아이콘은 homeNavigationIcon으로 전달)
                if (currentDestination != MainDestination.HOME) {
                    SgTopBar(
                        title = currentDestination.label,
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "메뉴")
                            }
                        },
                        actions = {
                            // 탭 쉘과 동일하게 상단바 우측에서도 알림 진입(알림 화면에서는 숨김)
                            if (currentDestination != MainDestination.NOTIFICATIONS) {
                                IconButton(onClick = { onDestinationSelected(MainDestination.NOTIFICATIONS) }) {
                                    Icon(Icons.Default.Notifications, contentDescription = "알림")
                                }
                            }
                        }
                    )
                }
            }
        ) { padding ->
            DestinationContent(
                destination = currentDestination,
                profile = profile,
                homeViewModel = homeViewModel,
                onOpenNotifications = { onDestinationSelected(MainDestination.NOTIFICATIONS) },
                onOpenSettings = onOpenSettings,
                onLogout = onLogout,
                homeNavigationIcon = {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(Icons.Default.Menu, contentDescription = "메뉴")
                    }
                },
                // 드로어 쉘은 하단 바가 없어 시스템 내비바 인셋을 콘텐츠가 직접 소화
                modifier = Modifier
                    .padding(padding)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .fillMaxSize()
            )
        }
    }
}

/** M2엔 NavigationDrawerItem이 없어 구 앱 NavigationView 항목을 직접 구성 */
@Composable
private fun DrawerItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val sg = SgTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) sg.accentSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) sg.accent else sg.inkSoft,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = SgTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) sg.accent else sg.ink
        )
    }
}

/** 구 앱 nav_header_main 미러 — 프로필 이미지 + 이름/이메일 (GET /api/users/me) */
@Composable
private fun DrawerHeader(profile: Profile?) {
    val sg = SgTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(sg.accent)
            // accent가 상태바 뒤까지 채워지도록 배경 뒤에 인셋 패딩
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(16.dp)
    ) {
        Spacer(Modifier.height(48.dp))
        SgAvatar(profile?.name ?: "?", size = 64.dp, containerColor = sg.accentSoft, contentColor = sg.accent)
        Spacer(Modifier.height(12.dp))
        Text(
            profile?.name ?: "불러오는 중...",
            style = SgTheme.typography.titleMedium,
            color = sg.onAccent
        )
        Text(profile?.email ?: "", style = SgTheme.typography.bodySmall, color = sg.onAccent)
        Spacer(Modifier.height(8.dp))
    }
}
