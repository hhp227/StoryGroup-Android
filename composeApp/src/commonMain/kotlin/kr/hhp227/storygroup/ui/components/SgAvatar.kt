package kr.hhp227.storygroup.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kr.hhp227.storygroup.ui.theme.SgTheme

/** 공용 아바타 — 프로필 이미지 연동 전, 이름 첫 글자 이니셜 원형 (기본 accent 바탕) */
@Composable
fun SgAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    containerColor: Color? = null,
    contentColor: Color? = null
) {
    val sg = SgTheme.colors

    Box(
        modifier = modifier.size(size).background(containerColor ?: sg.accent, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.take(1),
            style = if (size >= 56.dp) SgTheme.typography.titleLarge else SgTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = contentColor ?: sg.onAccent
        )
    }
}
