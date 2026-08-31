package kr.hhp227.storygroup.ui.screens.settings

import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.RadioButton
import androidx.compose.material.RadioButtonDefaults
import androidx.compose.material.Scaffold
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kr.hhp227.storygroup.di.screenViewModel
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
import storygroup.composeapp.generated.resources.app_settings_notifications
import storygroup.composeapp.generated.resources.app_settings_push_activity
import storygroup.composeapp.generated.resources.app_settings_push_activity_desc
import storygroup.composeapp.generated.resources.app_settings_push_chat
import storygroup.composeapp.generated.resources.app_settings_push_chat_desc
import storygroup.composeapp.generated.resources.app_settings_push_error_load
import storygroup.composeapp.generated.resources.app_settings_push_hint
import storygroup.composeapp.generated.resources.app_settings_theme_mood
import storygroup.composeapp.generated.resources.common_back
import storygroup.composeapp.generated.resources.common_retry
import storygroup.composeapp.generated.resources.profile_app_settings

/**
 * 앱 설정 — 테마(무드/화면 모드/내비게이션 스타일, ThemeState 직접 소비) + 알림(푸시 on/off, AppSettingsViewModel).
 * 쉘 위를 덮는 전체 화면. iosApp SGSettingsView.swift와 1:1 미러
 */
@Composable
fun AppSettingsScreen(
    themeState: ThemeState,
    onBack: () -> Unit,
    viewModel: AppSettingsViewModel = screenViewModel {
        AppSettingsViewModel(
            getPushPreferencesUseCase = it.getPushPreferencesUseCase,
            updatePushPreferencesUseCase = it.updatePushPreferencesUseCase
        )
    }
) {
    val uiState by viewModel.uiState.collectAsState()
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
            Spacer(Modifier.height(24.dp))
            SgSectionTitle(stringResource(Res.string.app_settings_notifications))
            Text(
                stringResource(Res.string.app_settings_push_hint),
                style = SgTheme.typography.bodySmall,
                color = SgTheme.colors.inkFaint,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            PushPreferencesSection(uiState = uiState, onAction = viewModel::onAction)
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

/** 알림 섹션 — 로드 전엔 스피너, 로드 실패는 문구+재시도, 그 외 토글 2행(+저장 실패 문구) */
@Composable
private fun PushPreferencesSection(
    uiState: AppSettingsViewModel.UiState,
    onAction: (AppSettingsViewModel.Action) -> Unit
) {
    val sg = SgTheme.colors
    val prefs = uiState.pushPreferences
    when {
        prefs == null && uiState.isLoading -> Box(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = sg.accent)
        }
        prefs == null -> Column(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                uiState.loadError ?: stringResource(Res.string.app_settings_push_error_load),
                style = SgTheme.typography.bodyMedium,
                color = sg.rust
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { onAction(AppSettingsViewModel.Action.Load) }) {
                Text(stringResource(Res.string.common_retry), color = sg.accent)
            }
        }
        else -> {
            SwitchRow(
                label = stringResource(Res.string.app_settings_push_chat),
                description = stringResource(Res.string.app_settings_push_chat_desc),
                checked = prefs.chatEnabled
            ) { onAction(AppSettingsViewModel.Action.SetChatEnabled(it)) }
            SwitchRow(
                label = stringResource(Res.string.app_settings_push_activity),
                description = stringResource(Res.string.app_settings_push_activity_desc),
                checked = prefs.activityEnabled
            ) { onAction(AppSettingsViewModel.Action.SetActivityEnabled(it)) }
            uiState.saveError?.let {
                Text(it, style = SgTheme.typography.bodySmall, color = sg.rust, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = SgTheme.typography.bodyMedium, color = SgTheme.colors.ink)
            Text(description, style = SgTheme.typography.bodySmall, color = SgTheme.colors.inkFaint)
        }
        Switch(
            checked = checked,
            // 행 전체가 toggleable이라 스위치 자체 클릭은 행에 맡긴다(이중 토글 방지)
            onCheckedChange = null,
            // RadioRow와 같은 이유 — M2 기본 선택색(secondary) 대신 accent로 고정
            colors = SwitchDefaults.colors(checkedThumbColor = SgTheme.colors.accent, checkedTrackColor = SgTheme.colors.accent)
        )
    }
}
