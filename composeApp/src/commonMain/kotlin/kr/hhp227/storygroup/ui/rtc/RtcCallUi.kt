package kr.hhp227.storygroup.ui.rtc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kr.hhp227.storygroup.shared.domain.model.RtcCallPeer
import kr.hhp227.storygroup.ui.components.SgAvatar
import kr.hhp227.storygroup.ui.theme.SgTheme

/**
 * 통화 비디오 그리드 — 로스터(PEERS) 기준 타일 2열, 회의 상세와 DM 통화 화면이 공유한다.
 * 비디오 없는 상대(오디오 전용·로스터 전용)는 아바타 폴백으로 그린다.
 */
@Composable
fun RtcVideoGrid(state: RtcCallController.State, myUserId: Long?, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.peers.chunked(2).forEach { rowPeers ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowPeers.forEach { peer ->
                    RtcPeerTile(
                        peer = peer,
                        isMe = peer.userId == myUserId,
                        state = state,
                        modifier = Modifier.weight(1f)
                    )
                }
                // 홀수 개일 때 마지막 행 반칸 유지
                if (rowPeers.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RtcPeerTile(
    peer: RtcCallPeer,
    isMe: Boolean,
    state: RtcCallController.State,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors
    val video = if (isMe) state.localVideo else state.remoteVideos[peer.userId]

    Box(
        modifier = modifier
            .aspectRatio(3f / 4f)
            .clip(SgTheme.shapes.card)
            .background(sg.linen),
        contentAlignment = Alignment.Center
    ) {
        // 공유 중 내 타일은 camOn과 무관하게 송출 중인 화면을 보여준다(localVideo가 화면 트랙, D9)
        if (video != null && (!isMe || state.camOn || state.sharing)) {
            RtcVideoView(track = video, modifier = Modifier.fillMaxSize())
        } else {
            SgAvatar(name = peer.userName, size = 48.dp)
        }
        Text(
            if (isMe) "나" else peer.userName,
            style = SgTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .background(Color.Black.copy(alpha = 0.45f), SgTheme.shapes.button)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/** 마이크/카메라 토글 — 켜짐(accent)/꺼짐(linen) 원형 아이콘 버튼 */
@Composable
fun RtcCallToggleButton(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors

    IconButton(
        onClick = onClick,
        modifier = modifier.background(if (active) sg.accentSoft else sg.linen, CircleShape)
    ) {
        Icon(icon, contentDescription = contentDescription, tint = if (active) sg.accent else sg.inkSoft)
    }
}
