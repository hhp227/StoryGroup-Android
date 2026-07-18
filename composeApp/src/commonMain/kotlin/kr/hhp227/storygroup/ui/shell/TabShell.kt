package kr.hhp227.storygroup.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.NavigationRail
import androidx.compose.material.NavigationRailItem
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kr.hhp227.storygroup.shared.domain.model.Profile
import kr.hhp227.storygroup.ui.components.SgTopBar
import kr.hhp227.storygroup.ui.theme.SgTheme

/** 레일 전환 폭 — M3 window size class의 compact/medium 경계(600dp) */
private val RailBreakpoint = 600.dp

/**
 * 기본 쉘: 웹 미러 헤더(linen+보더) + 5탭.
 * 폭 600dp 미만(폰)은 하단 탭, 이상(데스크톱·와이드 창)은 좌측 NavigationRail로 적응.
 */
@Composable
internal fun TabShell(
    currentDestination: MainDestination,
    onDestinationSelected: (MainDestination) -> Unit,
    profile: Profile?,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit
) {
    val sg = SgTheme.colors

    BoxWithConstraints {
        val useRail = maxWidth >= RailBreakpoint

        Row(Modifier.fillMaxSize()) {
            if (useRail) {
                NavigationRail(
                    backgroundColor = sg.linen,
                    elevation = 0.dp,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
                ) {
                    MainDestination.entries.filter { it.inTabs }.forEach { destination ->
                        NavigationRailItem(
                            selected = destination == currentDestination,
                            onClick = { onDestinationSelected(destination) },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                            selectedContentColor = sg.accent,
                            unselectedContentColor = sg.inkSoft
                        )
                    }
                }
                Box(Modifier.fillMaxHeight().width(1.dp).background(sg.stoneBorder))
            }
            Scaffold(
                backgroundColor = sg.paper,
                topBar = {
                    SgTopBar(
                        title = if (currentDestination == MainDestination.HOME) "우리들의 이야기" else currentDestination.label,
                        actions = {
                            if (currentDestination == MainDestination.HOME) {
                                IconButton(onClick = { /* TODO: 검색 */ }) {
                                    Icon(Icons.Default.Search, contentDescription = "검색")
                                }
                            }
                            // 알림은 탭에서 빠지고 상단바 종 아이콘으로 진입(알림 화면에서는 숨김)
                            if (currentDestination != MainDestination.NOTIFICATIONS) {
                                IconButton(onClick = { onDestinationSelected(MainDestination.NOTIFICATIONS) }) {
                                    Icon(Icons.Default.Notifications, contentDescription = "알림")
                                }
                            }
                            if (currentDestination == MainDestination.PROFILE) {
                                IconButton(onClick = onOpenSettings) {
                                    Icon(Icons.Default.Settings, contentDescription = "앱 설정")
                                }
                            }
                        }
                    )
                },
                bottomBar = {
                    if (!useRail) {
                        // M2 BottomNavigation은 인셋을 모름 — linen을 시스템 내비바 뒤까지 깔고 그만큼 패딩
                        Column(Modifier.background(sg.linen).windowInsetsPadding(WindowInsets.navigationBars)) {
                            Divider(color = sg.stoneBorder, thickness = 1.dp)
                            BottomNavigation(backgroundColor = sg.linen, elevation = 0.dp) {
                                MainDestination.entries.filter { it.inTabs }.forEach { destination ->
                                    BottomNavigationItem(
                                        selected = destination == currentDestination,
                                        onClick = { onDestinationSelected(destination) },
                                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                                        label = { Text(destination.label) },
                                        selectedContentColor = sg.accent,
                                        unselectedContentColor = sg.inkSoft
                                    )
                                }
                            }
                        }
                    }
                }
            ) { padding ->
                DestinationContent(
                    destination = currentDestination,
                    profile = profile,
                    onOpenSettings = onOpenSettings,
                    onLogout = onLogout,
                    // 레일 모드는 하단 바가 없어 내비바 인셋을 콘텐츠가 직접 소화(탭 모드는 하단 바가 소화)
                    modifier = Modifier
                        .padding(padding)
                        .let { if (useRail) it.windowInsetsPadding(WindowInsets.navigationBars) else it }
                        .fillMaxSize()
                )
            }
        }
    }
}
