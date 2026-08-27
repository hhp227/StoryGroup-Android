package kr.hhp227.storygroup.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.RadioButton
import androidx.compose.material.RadioButtonDefaults
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kr.hhp227.storygroup.ui.components.SgSectionTitle
import kr.hhp227.storygroup.ui.components.SgTopBar
import kr.hhp227.storygroup.ui.theme.Mood
import kr.hhp227.storygroup.ui.theme.NavStyle
import kr.hhp227.storygroup.ui.theme.NightMode
import kr.hhp227.storygroup.ui.theme.SgTheme
import kr.hhp227.storygroup.ui.theme.ThemeState
import org.jetbrains.compose.resources.stringResource
import storygroup.composeapp.generated.resources.Res
import storygroup.composeapp.generated.resources.app_settings_display_mode
import storygroup.composeapp.generated.resources.app_settings_mode_dark
import storygroup.composeapp.generated.resources.app_settings_mode_light
import storygroup.composeapp.generated.resources.app_settings_mode_system
import storygroup.composeapp.generated.resources.app_settings_mood_vibrant
import storygroup.composeapp.generated.resources.app_settings_mood_warm
import storygroup.composeapp.generated.resources.app_settings_nav_drawer
import storygroup.composeapp.generated.resources.app_settings_nav_style
import storygroup.composeapp.generated.resources.app_settings_nav_tabs
import storygroup.composeapp.generated.resources.app_settings_theme_mood
import storygroup.composeapp.generated.resources.common_back
import storygroup.composeapp.generated.resources.profile_app_settings

/** 앱 설정(테마) — 무드/화면 모드/내비게이션 스타일. 쉘 위를 덮는 전체 화면 */
@Composable
fun AppSettingsScreen(themeState: ThemeState, onBack: () -> Unit) {
    Scaffold(
        backgroundColor = SgTheme.colors.paper,
        topBar = {
            SgTopBar(
                title = stringResource(Res.string.profile_app_settings),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.common_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                // 하단 바 없는 전체 화면이라 시스템 내비바 인셋을 직접 소화
                .windowInsetsPadding(WindowInsets.navigationBars)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            SgSectionTitle(stringResource(Res.string.app_settings_theme_mood))
            RadioRow(stringResource(Res.string.app_settings_mood_warm), themeState.mood == Mood.WARM) { themeState.mood = Mood.WARM }
            RadioRow(stringResource(Res.string.app_settings_mood_vibrant), themeState.mood == Mood.VIBRANT) { themeState.mood = Mood.VIBRANT }
            Spacer(Modifier.height(24.dp))
            SgSectionTitle(stringResource(Res.string.app_settings_display_mode))
            RadioRow(stringResource(Res.string.app_settings_mode_system), themeState.nightMode == NightMode.SYSTEM) { themeState.nightMode = NightMode.SYSTEM }
            RadioRow(stringResource(Res.string.app_settings_mode_light), themeState.nightMode == NightMode.LIGHT) { themeState.nightMode = NightMode.LIGHT }
            RadioRow(stringResource(Res.string.app_settings_mode_dark), themeState.nightMode == NightMode.DARK) { themeState.nightMode = NightMode.DARK }
            Spacer(Modifier.height(24.dp))
            SgSectionTitle(stringResource(Res.string.app_settings_nav_style))
            RadioRow(stringResource(Res.string.app_settings_nav_tabs), themeState.navStyle == NavStyle.TABS) { themeState.navStyle = NavStyle.TABS }
            RadioRow(stringResource(Res.string.app_settings_nav_drawer), themeState.navStyle == NavStyle.DRAWER) { themeState.navStyle = NavStyle.DRAWER }
        }
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            // M2 기본 선택색은 secondary(accent2)라 M3 시절과 같은 accent로 고정
            colors = RadioButtonDefaults.colors(selectedColor = SgTheme.colors.accent)
        )
        Text(label, style = SgTheme.typography.bodyMedium, color = SgTheme.colors.ink)
    }
}
