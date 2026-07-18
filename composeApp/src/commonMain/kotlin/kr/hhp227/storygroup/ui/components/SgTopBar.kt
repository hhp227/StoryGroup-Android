package kr.hhp227.storygroup.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kr.hhp227.storygroup.ui.theme.SgTheme

/**
 * 공용 상단바 — 웹 헤더 미러(linen 바탕 + stone 하단 보더). M2 TopAppBar라 그림자 대신 보더 사용(elevation 0).
 * M2는 M3와 달리 인셋을 처리하지 않으므로 상태바 패딩을 여기서 직접 소화한다(linen이 상태바 뒤까지 채워짐).
 */
@Composable
fun SgTopBar(
    title: String,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val sg = SgTheme.colors

    Column(Modifier.background(sg.linen).windowInsetsPadding(WindowInsets.statusBars)) {
        TopAppBar(
            title = { Text(title, fontWeight = FontWeight.Bold, color = sg.ink) },
            navigationIcon = navigationIcon,
            actions = actions,
            backgroundColor = sg.linen,
            contentColor = sg.inkSoft,
            elevation = 0.dp
        )
        Divider(color = sg.stoneBorder, thickness = 1.dp)
    }
}
