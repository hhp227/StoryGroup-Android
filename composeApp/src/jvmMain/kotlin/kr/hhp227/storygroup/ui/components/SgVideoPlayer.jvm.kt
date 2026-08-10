package kr.hhp227.storygroup.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kr.hhp227.storygroup.ui.theme.SgTheme
import java.awt.Desktop
import java.net.URI

/**
 * Desktop 미지원 — Compose Desktop엔 내장 비디오 재생이 없고, 넣으려면 VLC 설치를 요구하는 VLCJ나
 * JavaFX 임베딩이 필요하다(RtcVideoView.jvm.kt와 같은 판단: 데스크톱은 미지원으로 둔다).
 * 대신 기본 브라우저로 넘긴다 — 서버 동영상이 공개 HTTPS URL이라 브라우저가 바로 재생한다.
 *
 * 사용자는 이미 ▶를 눌러 재생을 요청한 상태이므로 들어오자마자 브라우저를 연다(한 번 더 누르게 하지 않는다).
 */
@Composable
actual fun SgVideoPlayer(url: String, modifier: Modifier) {
    var failed by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        failed = !openInBrowser(url)
    }
    Column(
        modifier = modifier.background(Color.Black.copy(alpha = 0.85f)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            if (failed) "브라우저를 열지 못했습니다. 주소를 복사해 직접 열어주세요." else "브라우저에서 재생 중입니다.",
            style = SgTheme.typography.bodySmall,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        // 실패했을 땐 주소를 보여준다 — 데스크톱에서 손으로 옮길 수 있는 유일한 탈출구다
        if (failed) {
            Text(url, style = SgTheme.typography.labelSmall, color = Color.White, textAlign = TextAlign.Center)
        }
        OutlinedButton(onClick = { failed = !openInBrowser(url) }, shape = SgTheme.shapes.button) {
            Text("브라우저에서 열기", color = Color.White)
        }
    }
}

/** 열기에 성공했으면 true — headless 환경이나 브라우저가 없는 경우를 조용히 넘기지 않고 화면에 알린다 */
private fun openInBrowser(url: String): Boolean = runCatching {
    if (!Desktop.isDesktopSupported()) return false
    val desktop = Desktop.getDesktop()

    if (!desktop.isSupported(Desktop.Action.BROWSE)) return false
    desktop.browse(URI(url))
    true
}.getOrDefault(false)
