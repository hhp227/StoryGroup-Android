package kr.hhp227.storygroup.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kr.hhp227.storygroup.ui.theme.SgTheme

/**
 * 데스크톱 스플래시 — ConCafe DesktopLaunchScreen 미러(본 화면 전환 전 잠깐 보이는 브랜드 화면).
 * 리포에 그래픽 로고 에셋이 없어 웹과 같은 텍스트 브랜드(워드마크)를 쓴다 — 로고가 생기면 Image로 교체.
 */
@Composable
fun DesktopLaunchScreen() {
    val sg = SgTheme.colors

    Box(
        modifier = Modifier.fillMaxSize().background(sg.paper),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "StoryGroup",
            style = SgTheme.typography.headlineSmall.copy(fontSize = 32.sp, lineHeight = 40.sp),
            fontWeight = FontWeight.Bold,
            color = sg.accent
        )
    }
}
