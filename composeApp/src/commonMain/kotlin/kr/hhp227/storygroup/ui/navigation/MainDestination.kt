package kr.hhp227.storygroup.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.StringResource
import storygroup.composeapp.generated.resources.Res
import storygroup.composeapp.generated.resources.nav_chat
import storygroup.composeapp.generated.resources.nav_friends
import storygroup.composeapp.generated.resources.nav_groups
import storygroup.composeapp.generated.resources.nav_home
import storygroup.composeapp.generated.resources.nav_notifications
import storygroup.composeapp.generated.resources.nav_profile

/** 내비 목적지 — 탭/레일은 5개(홈·그룹·친구·채팅·프로필), 알림은 상단바 종 아이콘으로 진입(레거시 드로어는 전부 노출) */
enum class MainDestination(val labelRes: StringResource, val icon: ImageVector, val inTabs: Boolean) {
    HOME(Res.string.nav_home, Icons.Default.Home, true),
    GROUPS(Res.string.nav_groups, Icons.Default.Groups, true),
    FRIENDS(Res.string.nav_friends, Icons.Default.People, true),
    CHAT(Res.string.nav_chat, Icons.AutoMirrored.Filled.Chat, true),
    NOTIFICATIONS(Res.string.nav_notifications, Icons.Default.Notifications, false),
    PROFILE(Res.string.nav_profile, Icons.Default.Person, true)
}
