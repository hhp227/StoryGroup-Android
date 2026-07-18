package kr.hhp227.storygroup.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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

/** 앱 설정(테마) — 무드/화면 모드/내비게이션 스타일. 쉘 위를 덮는 전체 화면 */
@Composable
fun AppSettingsScreen(themeState: ThemeState, onBack: () -> Unit) {
    val sg = SgTheme.colors

    Scaffold(
        backgroundColor = sg.paper,
        topBar = {
            SgTopBar(
                title = "앱 설정",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            SgSectionTitle("테마 무드")
            RadioRow("다정함 (웜)", themeState.mood == Mood.WARM) { themeState.mood = Mood.WARM }
            RadioRow("캐주얼 (비비드)", themeState.mood == Mood.VIBRANT) { themeState.mood = Mood.VIBRANT }
            Spacer(Modifier.height(24.dp))
            SgSectionTitle("화면 모드")
            RadioRow("시스템 설정", themeState.nightMode == NightMode.SYSTEM) { themeState.nightMode = NightMode.SYSTEM }
            RadioRow("라이트", themeState.nightMode == NightMode.LIGHT) { themeState.nightMode = NightMode.LIGHT }
            RadioRow("다크", themeState.nightMode == NightMode.DARK) { themeState.nightMode = NightMode.DARK }
            Spacer(Modifier.height(24.dp))
            SgSectionTitle("내비게이션 스타일")
            RadioRow("기본 (탭 — 넓은 화면은 레일)", themeState.navStyle == NavStyle.TABS) { themeState.navStyle = NavStyle.TABS }
            RadioRow("레거시 (드로어)", themeState.navStyle == NavStyle.DRAWER) { themeState.navStyle = NavStyle.DRAWER }
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
