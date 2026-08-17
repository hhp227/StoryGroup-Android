package kr.hhp227.storygroup.ui.screens.call

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.automirrored.filled.StopScreenShare
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.hhp227.storygroup.di.LocalAppContainer
import kr.hhp227.storygroup.ui.components.SgTopBar
import kr.hhp227.storygroup.ui.navigation.NavigationAction
import kr.hhp227.storygroup.ui.navigation.sessionNavigationViewModel
import kr.hhp227.storygroup.ui.rtc.RtcCallToggleButton
import kr.hhp227.storygroup.ui.rtc.RtcVideoGrid
import kr.hhp227.storygroup.ui.rtc.rememberRtcMediaSessionFactory
import kr.hhp227.storygroup.ui.rtc.rememberRtcPermissionsRequester
import kr.hhp227.storygroup.ui.rtc.rememberRtcScreenCaptureRequester
import kr.hhp227.storygroup.ui.theme.SgTheme

@Composable
private fun callViewModel(chatRoomId: Long, ring: Boolean, video: Boolean): CallViewModel {
    val container = LocalAppContainer.current
    // 플랫폼 미디어 팩토리 — Android는 applicationContext 캡처라 VM 보관이 안전, Desktop은 null 생성
    val rtcMediaSessionFactory = rememberRtcMediaSessionFactory()

    return viewModel(key = "call-$chatRoomId") {
        CallViewModel(
            chatRoomId = chatRoomId,
            ring = ring,
            video = video,
            observeRtcCallEventsUseCase = container.observeRtcCallEventsUseCase,
            observeRtcSignalsUseCase = container.observeRtcSignalsUseCase,
            sendRtcSignalUseCase = container.sendRtcSignalUseCase,
            getIceServersUseCase = container.getIceServersUseCase,
            sendCallInviteUseCase = container.sendCallInviteUseCase,
            rtcMediaSessionFactory = rtcMediaSessionFactory,
            getCurrentUserIdUseCase = container.getCurrentUserIdUseCase
        )
    }
}

/**
 * 방 통화 — DM 1:1과 그룹 방 공용(페이스톡 미러). 진입 즉시 권한을 물어 통화에 입장한다
 * (발신=벨울림 포함, 수신=배너 수락으로 진입). 그리드/토글은 ui/rtc/RtcCallUi 공용 —
 * 1:1이라도 "2명짜리 메시"로 같은 코드 경로(웹 D1). iosApp CallView.swift와 1:1 미러
 */
@Composable
fun CallScreen(
    chatRoomId: Long,
    title: String,
    ring: Boolean,
    // false면 보이스톡 — 카메라 OFF·수화구로 시작(통화 중 카메라를 켜면 페이스톡 전환)
    video: Boolean,
    modifier: Modifier = Modifier,
    onNavigationAction: (NavigationAction) -> Unit = sessionNavigationViewModel()::onAction,
    // 라우트(백스택 엔트리) 스코프 — pop되면 구독(=통화)도 함께 정리된다
    viewModel: CallViewModel = callViewModel(chatRoomId, ring, video)
) {
    val uiState by viewModel.uiState.collectAsState()
    val onAction = viewModel::onAction
    val sg = SgTheme.colors
    val requestJoin = rememberRtcPermissionsRequester { granted ->
        onAction(CallViewModel.Action.Join(withMedia = granted))
    }
    // 화면 캡처 동의(MediaProjection) — 거부(null)는 웹 getDisplayMedia 취소처럼 조용히 무시한다
    val requestScreenCapture = rememberRtcScreenCaptureRequester { grant ->
        if (grant != null) onAction(CallViewModel.Action.StartScreenShare(grant))
    }

    // 진입 즉시 참가 — 이미 통화 중(재진입/회전)이면 VM 가드가 무시한다
    LaunchedEffect(viewModel) {
        if (!viewModel.uiState.value.call.isInCall) requestJoin()
    }
    LaunchedEffect(Unit) {
        viewModel.event.collect { event ->
            when (event) {
                CallViewModel.Event.Ended -> onNavigationAction(NavigationAction.NavigateBack)
            }
        }
    }
    Column(modifier.fillMaxSize().background(sg.paper)) {
        SgTopBar(
            title = title,
            navigationIcon = {
                // 뒤로가기도 끊기와 동일 — 통화를 정리하고 나간다
                IconButton(onClick = { onAction(CallViewModel.Action.HangUp) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                }
            }
        )
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                when {
                    // 발신 무응답 — 잠깐 보여준 뒤 VM이 Ended로 pop한다
                    uiState.isNoAnswer -> "응답이 없어 통화를 종료합니다."
                    !uiState.call.isInCall -> "연결 중…"
                    !uiState.call.isConnected -> "재연결 중…"
                    uiState.isAloneInCall && uiState.isRinging -> "응답을 기다리는 중…"
                    uiState.isAloneInCall -> "아직 다른 참여자가 없습니다."
                    else -> "통화 중"
                },
                style = SgTheme.typography.bodyMedium,
                color = sg.inkSoft
            )
            RtcVideoGrid(state = uiState.call, myUserId = uiState.myUserId)
            if (uiState.call.isInCall && !uiState.call.isMediaActive) {
                // 권한 거부(Android)·미지원 플랫폼(Desktop) — 명단만 실시간으로 표시된다
                Text(
                    "카메라·마이크 없이 참여 중입니다.",
                    style = SgTheme.typography.bodySmall,
                    color = sg.inkFaint
                )
            }
        }
        // 통화 컨트롤 바 — 하단 고정(토글은 미디어 활성 시에만)
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (uiState.call.isMediaActive) {
                RtcCallToggleButton(
                    icon = if (uiState.call.micOn) Icons.Default.Mic else Icons.Default.MicOff,
                    contentDescription = if (uiState.call.micOn) "마이크 끄기" else "마이크 켜기",
                    active = uiState.call.micOn,
                    onClick = { onAction(CallViewModel.Action.ToggleMic) }
                )
                RtcCallToggleButton(
                    icon = if (uiState.call.camOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                    contentDescription = if (uiState.call.camOn) "카메라 끄기" else "카메라 켜기",
                    active = uiState.call.camOn,
                    onClick = { onAction(CallViewModel.Action.ToggleCam) }
                )
                // 카메라 전환(전/후면) — 카메라가 있을 때만. 공유 중엔 로컬이 화면 트랙이라 숨김
                if (uiState.call.localVideo != null && !uiState.call.sharing) {
                    RtcCallToggleButton(
                        icon = Icons.Default.Cameraswitch,
                        contentDescription = "카메라 전환",
                        active = true,
                        onClick = { onAction(CallViewModel.Action.SwitchCamera) }
                    )
                }
                // 스피커폰 — 영상통화라 기본 ON, 끄면 수화구·이어폰 경로(웹엔 없는 모바일 전용)
                RtcCallToggleButton(
                    icon = if (uiState.call.speakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    contentDescription = if (uiState.call.speakerOn) "스피커 끄기" else "스피커 켜기",
                    active = uiState.call.speakerOn,
                    onClick = { onAction(CallViewModel.Action.ToggleSpeaker) }
                )
                // 오디오 전용(카메라 실패)이면 video sender가 없어 replaceTrack 불가 — 버튼 숨김(웹 D9)
                if (uiState.call.localVideo != null) {
                    RtcCallToggleButton(
                        icon = if (uiState.call.sharing) Icons.AutoMirrored.Filled.StopScreenShare else Icons.AutoMirrored.Filled.ScreenShare,
                        contentDescription = if (uiState.call.sharing) "화면 공유 중지" else "화면 공유",
                        active = uiState.call.sharing,
                        onClick = {
                            if (uiState.call.sharing) onAction(CallViewModel.Action.StopScreenShare)
                            else requestScreenCapture()
                        }
                    )
                }
            }
            IconButton(
                onClick = { onAction(CallViewModel.Action.HangUp) },
                modifier = Modifier.background(sg.rust, CircleShape)
            ) {
                Icon(Icons.Default.CallEnd, contentDescription = "통화 끊기", tint = sg.onAccent)
            }
        }
    }
}
