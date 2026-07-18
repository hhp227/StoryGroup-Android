package kr.hhp227.storygroup.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kr.hhp227.storygroup.ui.theme.SgTheme

/** 공용 섹션 제목 — 설정 그룹/채팅 허브 섹션 등 리스트 구획 라벨 */
@Composable
fun SgSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = SgTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = SgTheme.colors.inkSoft,
        modifier = modifier.padding(bottom = 4.dp)
    )
}
