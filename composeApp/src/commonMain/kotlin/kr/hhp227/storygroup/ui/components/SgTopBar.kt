package kr.hhp227.storygroup.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kr.hhp227.storygroup.ui.theme.SgTheme

/** 공용 상단바 — 웹 헤더 미러(linen 바탕 + stone 하단 보더). M2 TopAppBar라 그림자 대신 보더 사용(elevation 0) */
@Composable
fun SgTopBar(
    title: String,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val sg = SgTheme.colors

    Column {
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
