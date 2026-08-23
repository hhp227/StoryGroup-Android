package kr.hhp227.storygroup.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kr.hhp227.storygroup.shared.domain.model.NetworkAlertState
import kr.hhp227.storygroup.ui.theme.SgTheme

/** 네트워크 연결 배너 — 오프라인=rust, 복구=moss. iosApp NetworkStatusBannerView.swift와 1:1 미러 */
@Composable
fun NetworkStatusBanner(
    networkAlertState: NetworkAlertState,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors
    val tint = if (networkAlertState.isConnected) sg.moss else sg.rust

    AnimatedVisibility(
        visible = networkAlertState.isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.statusBarsPadding()
    ) {
        Surface(
            // 반투명 틴트를 paper 위에 합성 — 어떤 화면 위에서도 불투명하게 보인다
            color = tint.copy(alpha = 0.14f).compositeOver(sg.paper),
            contentColor = tint,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = networkAlertState.message,
                style = SgTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
            )
        }
    }
}
