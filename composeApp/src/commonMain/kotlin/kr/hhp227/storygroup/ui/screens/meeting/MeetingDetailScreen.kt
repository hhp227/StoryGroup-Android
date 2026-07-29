package kr.hhp227.storygroup.ui.screens.meeting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.hhp227.storygroup.di.LocalAppContainer
import kr.hhp227.storygroup.shared.domain.model.Meeting
import kr.hhp227.storygroup.shared.domain.model.MeetingCallPeer
import kr.hhp227.storygroup.shared.domain.model.MeetingParticipant
import kr.hhp227.storygroup.ui.components.SgAvatar
import kr.hhp227.storygroup.ui.components.SgCard
import kr.hhp227.storygroup.ui.components.SgPrimaryButton
import kr.hhp227.storygroup.ui.components.SgPullRefreshBox
import kr.hhp227.storygroup.ui.components.SgSectionTitle
import kr.hhp227.storygroup.ui.components.SgTopBar
import kr.hhp227.storygroup.ui.rtc.RtcVideoView
import kr.hhp227.storygroup.ui.rtc.rememberRtcMediaSessionFactory
import kr.hhp227.storygroup.ui.rtc.rememberRtcPermissionsRequester
import kr.hhp227.storygroup.ui.theme.SgTheme
import kr.hhp227.storygroup.ui.util.formatRelativeTime

@Composable
private fun meetingDetailViewModel(groupId: Long, meetingId: Long): MeetingDetailViewModel {
    val container = LocalAppContainer.current
    // 플랫폼 미디어 팩토리 — Android는 applicationContext 캡처라 VM 보관이 안전, Desktop은 null 생성
    val rtcMediaSessionFactory = rememberRtcMediaSessionFactory()

    return viewModel(key = "meeting-detail-$meetingId") {
        MeetingDetailViewModel(
            groupId = groupId,
            meetingId = meetingId,
            getMeetingUseCase = container.getMeetingUseCase,
            getMeetingParticipantsUseCase = container.getMeetingParticipantsUseCase,
            joinMeetingUseCase = container.joinMeetingUseCase,
            leaveMeetingUseCase = container.leaveMeetingUseCase,
            endMeetingUseCase = container.endMeetingUseCase,
            observeMeetingCallEventsUseCase = container.observeMeetingCallEventsUseCase,
            observeMeetingRtcSignalsUseCase = container.observeMeetingRtcSignalsUseCase,
            sendMeetingRtcSignalUseCase = container.sendMeetingRtcSignalUseCase,
            getIceServersUseCase = container.getIceServersUseCase,
            rtcMediaSessionFactory = rtcMediaSessionFactory,
            getCurrentUserIdUseCase = container.getCurrentUserIdUseCase
        )
    }
}

/**
 * 회의 상세 — 회의 정보+실시간 영상 통화(비디오 그리드)+참가 기록(웹 meeting-detail 미러).
 * 통화 참가는 rtc 토픽 구독(=입장)이고, 미디어는 카메라/마이크 권한 허용 시에만 붙는다
 * (거부/미지원 플랫폼은 로스터 전용). iosApp MeetingDetailView.swift와 1:1 미러.
 */
@Composable
fun MeetingDetailScreen(
    groupId: Long,
    meetingId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    // 라우트(백스택 엔트리) 스코프 — pop되면 구독(=통화)도 함께 정리된다
    viewModel: MeetingDetailViewModel = meetingDetailViewModel(groupId, meetingId)
) {
    MeetingDetailContent(
        viewModel = viewModel,
        onBack = onBack,
        modifier = modifier
    )
}

@Composable
private fun MeetingDetailContent(
    viewModel: MeetingDetailViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val onAction = viewModel::onAction
    val sg = SgTheme.colors
    // 참가 버튼 → 권한 요청 → 결과(허용=미디어, 거부=로스터 전용)로 참가
    val requestJoinCall = rememberRtcPermissionsRequester { granted ->
        onAction(MeetingDetailViewModel.Action.JoinCall(withMedia = granted))
    }

    // 상세 진입 시 신선화 — VM이 유지되므로 재진입 때도 최신화된다
    LaunchedEffect(viewModel) {
        viewModel.onAction(MeetingDetailViewModel.Action.Refresh)
    }
    Column(modifier.fillMaxSize().background(sg.paper)) {
        SgTopBar(
            title = "회의 #${viewModel.meetingId}",
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                }
            }
        )
        SgPullRefreshBox(
            refreshing = uiState.isLoading && uiState.meeting != null,
            onRefresh = { onAction(MeetingDetailViewModel.Action.Refresh) }
        ) {
            when {
                uiState.meeting == null && uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = sg.accent)
                }
                uiState.meeting == null && uiState.error != null -> Column(
                    modifier = Modifier.fillMaxSize().padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(uiState.error.orEmpty(), style = SgTheme.typography.bodyMedium, color = sg.rust)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { onAction(MeetingDetailViewModel.Action.Refresh) }) {
                        Text("다시 시도", color = sg.accent)
                    }
                }
                else -> uiState.meeting?.let { meeting ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item(key = "meeting-info") {
                            MeetingInfoCard(meeting = meeting, hostName = uiState.hostName)
                        }
                        if (meeting.isActive) {
                            item(key = "call") {
                                CallSection(
                                    uiState = uiState,
                                    onJoin = requestJoinCall,
                                    onLeave = { onAction(MeetingDetailViewModel.Action.LeaveCall) },
                                    onToggleMic = { onAction(MeetingDetailViewModel.Action.ToggleMic) },
                                    onToggleCam = { onAction(MeetingDetailViewModel.Action.ToggleCam) }
                                )
                            }
                            if (uiState.isHost) {
                                item(key = "end-meeting") {
                                    OutlinedButton(
                                        onClick = { onAction(MeetingDetailViewModel.Action.EndMeeting) },
                                        enabled = !uiState.isEnding,
                                        shape = SgTheme.shapes.button,
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = sg.rust),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(if (uiState.isEnding) "종료 중…" else "회의 종료", style = SgTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                        uiState.actionError?.let { actionError ->
                            item(key = "action-error") {
                                Text(actionError, style = SgTheme.typography.bodySmall, color = sg.rust)
                            }
                        }
                        if (uiState.participants.isNotEmpty()) {
                            item(key = "participants-title") {
                                SgSectionTitle("참가 기록 ${uiState.participants.size}")
                            }
                            items(uiState.participants, key = { "participant-${it.userId}-${it.joinedAt}" }) { participant ->
                                ParticipantRow(participant = participant, isHost = participant.userId == meeting.hostId)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MeetingInfoCard(meeting: Meeting, hostName: String?, modifier: Modifier = Modifier) {
    val sg = SgTheme.colors

    SgCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "회의 #${meeting.id}",
                    style = SgTheme.typography.titleMedium,
                    color = sg.ink,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                MeetingStatusBadge(isActive = meeting.isActive)
            }
            Text(
                buildString {
                    append("시작 ${formatRelativeTime(meeting.startedAt)}")
                    meeting.endedAt?.let { append(" · 종료 ${formatRelativeTime(it)}") }
                },
                style = SgTheme.typography.bodySmall,
                color = sg.inkSoft
            )
            hostName?.let {
                Text("호스트 $it", style = SgTheme.typography.bodySmall, color = sg.inkSoft)
            }
        }
    }
}

/**
 * 통화 섹션 — 참가 전엔 버튼만, 참가 중엔 비디오 그리드(미디어)나 아바타 로스터(로스터 전용)와
 * 마이크/카메라 토글·나가기. PEERS가 전체 목록이라 그리드는 로스터 기준으로 그린다.
 */
@Composable
private fun CallSection(
    uiState: MeetingDetailViewModel.UiState,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
    onToggleMic: () -> Unit,
    onToggleCam: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors

    SgCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "실시간 통화",
                    style = SgTheme.typography.titleMedium,
                    color = sg.ink,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (uiState.isInCall) {
                    Text(
                        if (uiState.isCallConnected) "통화 중 ${uiState.callPeers.size}명" else "재연결 중…",
                        style = SgTheme.typography.labelSmall,
                        color = if (uiState.isCallConnected) sg.accent2 else sg.inkSoft
                    )
                }
            }
            if (uiState.isInCall) {
                if (uiState.isMediaActive) {
                    VideoGrid(uiState = uiState)
                } else {
                    RosterOnlyStrip(uiState = uiState)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.isMediaActive) {
                        CallToggleButton(
                            icon = if (uiState.micOn) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = if (uiState.micOn) "마이크 끄기" else "마이크 켜기",
                            active = uiState.micOn,
                            onClick = onToggleMic
                        )
                        CallToggleButton(
                            icon = if (uiState.camOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                            contentDescription = if (uiState.camOn) "카메라 끄기" else "카메라 켜기",
                            active = uiState.camOn,
                            onClick = onToggleCam
                        )
                    }
                    OutlinedButton(
                        onClick = onLeave,
                        shape = SgTheme.shapes.button,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = sg.rust),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("통화 나가기", style = SgTheme.typography.labelLarge)
                    }
                }
                if (!uiState.isMediaActive) {
                    // 권한 거부(Android)·미지원 플랫폼(Desktop) — 명단만 실시간으로 표시된다
                    Text(
                        "카메라·마이크 없이 참여 중입니다. 통화 명단만 실시간으로 표시됩니다.",
                        style = SgTheme.typography.bodySmall,
                        color = sg.inkFaint
                    )
                }
            } else {
                SgPrimaryButton(
                    text = "통화 참가",
                    onClick = onJoin,
                    isLoading = uiState.isJoining,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** 비디오 그리드 — 로스터(PEERS) 기준 타일 2열. 비디오 없는 상대(오디오 전용)는 아바타 폴백 */
@Composable
private fun VideoGrid(uiState: MeetingDetailViewModel.UiState, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        uiState.callPeers.chunked(2).forEach { rowPeers ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowPeers.forEach { peer ->
                    PeerTile(
                        peer = peer,
                        isMe = peer.userId == uiState.myUserId,
                        uiState = uiState,
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
private fun PeerTile(
    peer: MeetingCallPeer,
    isMe: Boolean,
    uiState: MeetingDetailViewModel.UiState,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors
    val video = if (isMe) uiState.localVideo else uiState.remoteVideos[peer.userId]

    Box(
        modifier = modifier
            .aspectRatio(3f / 4f)
            .clip(SgTheme.shapes.card)
            .background(sg.linen),
        contentAlignment = Alignment.Center
    ) {
        if (video != null && (!isMe || uiState.camOn)) {
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

/** 로스터 전용 표시(미디어 없음) — 1차 마일스톤의 아바타 스트립 유지 */
@Composable
private fun RosterOnlyStrip(uiState: MeetingDetailViewModel.UiState, modifier: Modifier = Modifier) {
    val sg = SgTheme.colors

    if (uiState.callPeers.isEmpty()) {
        Text("통화 명단을 불러오는 중…", style = SgTheme.typography.bodySmall, color = sg.inkSoft, modifier = modifier)
    } else {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = modifier) {
            items(uiState.callPeers, key = MeetingCallPeer::userId) { peer ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SgAvatar(name = peer.userName)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (peer.userId == uiState.myUserId) "나" else peer.userName,
                        style = SgTheme.typography.labelSmall,
                        color = sg.inkSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun CallToggleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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

@Composable
private fun ParticipantRow(participant: MeetingParticipant, isHost: Boolean, modifier: Modifier = Modifier) {
    val sg = SgTheme.colors

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SgAvatar(name = participant.name, imageUrl = participant.profileImg)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    participant.name,
                    style = SgTheme.typography.bodyMedium,
                    color = sg.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isHost) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "호스트",
                        style = SgTheme.typography.labelSmall,
                        color = sg.accent,
                        modifier = Modifier
                            .background(sg.accentSoft, SgTheme.shapes.button)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                if (participant.isActive) "참여 중 · ${formatRelativeTime(participant.joinedAt)} 참가"
                else "나감 · ${formatRelativeTime(participant.leftAt.orEmpty())}",
                style = SgTheme.typography.bodySmall,
                color = if (participant.isActive) sg.accent2 else sg.inkSoft
            )
        }
    }
}
