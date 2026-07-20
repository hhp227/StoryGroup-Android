package kr.hhp227.storygroup.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kr.hhp227.storygroup.ui.theme.SgTheme

/** 공용 아바타 — imageUrl이 있으면 실제 이미지, 없으면 이름 첫 글자 이니셜 원형(기본 accent 바탕) */
@Composable
fun SgAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    imageUrl: String? = null,
    containerColor: Color? = null,
    contentColor: Color? = null
) {
    val sg = SgTheme.colors

    Box(
        modifier = modifier.size(size).background(containerColor ?: sg.accent, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl != null) {
            // 이니셜이 배경에 깔려 있어 로딩 지연·실패 시에도 빈 원이 아니라 폴백처럼 보인다
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(CircleShape)
            )
        } else {
            Text(
                name.take(1),
                style = if (size >= 56.dp) SgTheme.typography.titleLarge else SgTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor ?: sg.onAccent
            )
        }
    }
}
