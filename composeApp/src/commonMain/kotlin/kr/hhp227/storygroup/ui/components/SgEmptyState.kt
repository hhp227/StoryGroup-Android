package kr.hhp227.storygroup.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kr.hhp227.storygroup.ui.theme.SgTheme

/** 공용 빈 상태 — 아이콘(선택) + 제목 + 설명, 화면 중앙 정렬 */
@Composable
fun SgEmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val sg = SgTheme.colors

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier.size(64.dp).background(sg.accentSoft, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = sg.accent, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(16.dp))
        }
        Text(title, style = SgTheme.typography.titleLarge, color = sg.ink)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, style = SgTheme.typography.bodyMedium, color = sg.inkFaint)
    }
}
