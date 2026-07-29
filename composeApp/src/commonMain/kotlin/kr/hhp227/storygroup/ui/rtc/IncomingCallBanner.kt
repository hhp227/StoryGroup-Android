package kr.hhp227.storygroup.ui.rtc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kr.hhp227.storygroup.ui.components.SgAvatar
import kr.hhp227.storygroup.ui.components.SgCard
import kr.hhp227.storygroup.ui.theme.SgTheme

/** DM 수신 통화 배너 — 셸 위 오버레이로 뜨는 수락/거절 카드(웹 헤더 배너 미러) */
@Composable
fun IncomingCallBanner(
    call: IncomingCallViewModel.IncomingCall,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors

    SgCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SgAvatar(name = call.callerName)
            Spacer(Modifier.width(12.dp))
            Text(
                "${call.callerName}님의 통화",
                style = SgTheme.typography.bodyMedium,
                color = sg.ink,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = onDecline,
                colors = ButtonDefaults.textButtonColors(contentColor = sg.inkSoft)
            ) {
                Text("거절", style = SgTheme.typography.labelLarge)
            }
            IconButton(
                onClick = onAccept,
                modifier = Modifier.background(sg.accent, CircleShape)
            ) {
                Icon(Icons.Default.Call, contentDescription = "수락", tint = sg.onAccent)
            }
        }
    }
}
