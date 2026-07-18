package kr.hhp227.storygroup.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.Card
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kr.hhp227.storygroup.ui.theme.SgTheme

/** 공용 카드 — 웹 .card 미러(linen 바탕 + stone 보더, 무드별 radius는 SgTheme.shapes.card) */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SgCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val sg = SgTheme.colors
    val shape = SgTheme.shapes.card
    val border = BorderStroke(1.dp, sg.stoneBorder)

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            backgroundColor = sg.linen,
            border = border,
            elevation = 1.dp
        ) {
            Column(content = content)
        }
    } else {
        Card(
            modifier = modifier,
            shape = shape,
            backgroundColor = sg.linen,
            border = border,
            elevation = 1.dp
        ) {
            Column(content = content)
        }
    }
}
