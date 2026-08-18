package kr.hhp227.storygroup.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

/** 내비 목적지 — 탭/레일은 5개(홈·그룹·친구·채팅·프로필), 알림은 상단바 종 아이콘으로 진입(레거시 드로어는 전부 노출) */
enum class MainDestination(val label: String, val icon: ImageVector, val inTabs: Boolean) {
    HOME("홈", Icons.Default.Home, true),
    GROUPS("그룹", Icons.Default.Groups, true),
    FRIENDS("친구", Icons.Default.People, true),
    CHAT("채팅", Icons.AutoMirrored.Filled.Chat, true),
    NOTIFICATIONS("알림", Icons.Default.Notifications, false),
    PROFILE("프로필", Icons.Default.Person, true)
}
