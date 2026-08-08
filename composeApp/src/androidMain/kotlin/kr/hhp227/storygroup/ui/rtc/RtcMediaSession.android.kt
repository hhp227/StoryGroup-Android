package kr.hhp227.storygroup.ui.rtc

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kr.hhp227.storygroup.shared.domain.model.IceServer
import kr.hhp227.storygroup.shared.domain.model.RtcSignalType
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpTransceiver
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

/** 팩토리는 applicationContext만 캡처한다 — VM(라우트 스코프)에 넘겨 보관해도 누수가 없다 */
@Composable
actual fun rememberRtcMediaSessionFactory(): RtcMediaSessionFactory {
    val appContext = LocalContext.current.applicationContext

    return remember { AndroidRtcMediaSessionFactory(appContext) }
}

/** 참가 시점 권한 게이트 — 이미 허용이면 다이얼로그 없이 즉시 true(웹 getUserMedia 프롬프트 미러) */
@Composable
actual fun rememberRtcPermissionsRequester(onResult: (granted: Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        currentOnResult(grants.values.all { it })
    }

    return {
        val permissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

        if (permissions.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) {
            currentOnResult(true)
        } else {
            launcher.launch(permissions)
        }
    }
}

/** 화면 캡처 동의 런처 — MediaProjection 시스템 다이얼로그, 거부하면 null(웹 getDisplayMedia 취소 미러) */
@Composable
actual fun rememberRtcScreenCaptureRequester(onResult: (grant: RtcScreenCaptureGrant?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data

        currentOnResult(
            if (result.resultCode == Activity.RESULT_OK && data != null) AndroidRtcScreenCaptureGrant(data) else null
        )
    }

    return {
        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        launcher.launch(manager.createScreenCaptureIntent())
    }
}

/** MediaProjection 동의 결과 — ScreenCapturerAndroid가 이 Intent로 프로젝션을 연다 */
internal class AndroidRtcScreenCaptureGrant(val data: Intent) : RtcScreenCaptureGrant

internal class AndroidRtcMediaSessionFactory(private val appContext: Context) : RtcMediaSessionFactory {
    override fun create(iceServers: List<IceServer>): RtcMediaSession = AndroidRtcMediaSession(appContext, iceServers)
}

/** 렌더러(RtcVideoView.android)가 소비하는 트랙+EGL 컨텍스트 묶음 — 로컬 미리보기만 미러 */
internal class AndroidRtcVideoTrackHandle(
    val track: VideoTrack,
    val eglContext: EglBase.Context,
    val mirror: Boolean
) : RtcVideoTrackHandle

/**
 * libwebrtc 기반 미디어 세션 — 웹 use-rtc-session의 Android판.
 * 시그널 payload는 웹과 같은 JSON({type,sdp} / {candidate,sdpMid,sdpMLineIndex})으로 상호운용된다.
 * libwebrtc 콜백은 내부 시그널링 스레드에서 오므로 피어 맵은 lock으로 보호하고,
 * 밖으로는 스레드 안전한 Flow만 노출한다. 카메라 실패 시 오디오 전용으로 계속한다(웹 폴백 미러).
 */
internal class AndroidRtcMediaSession(
    private val appContext: Context,
    iceServers: List<IceServer>
) : RtcMediaSession {

    private val lock = Any()
    private val eglBase: EglBase = EglBase.create()
    private val factory: PeerConnectionFactory
    private val rtcConfig: PeerConnection.RTCConfiguration
    private val peers = mutableMapOf<Long, PeerHandle>()
    private var audioSource: AudioSource? = null
    private var videoSource: VideoSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var localVideoTrack: VideoTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceHelper: SurfaceTextureHelper? = null
    // 카메라 캡처가 실제로 도는지 — 보이스톡(camEnabled=false 시작)은 캡처러만 만들어 두고 열지 않는다
    private var captureRunning = false
    // 화면 공유 자원 — 카메라와 별도 파이프라인(카메라는 stop하지 않고 보관, 웹 D9 미러)
    private var screenCapturer: VideoCapturer? = null
    private var screenSource: VideoSource? = null
    private var screenHelper: SurfaceTextureHelper? = null
    private var screenVideoTrack: VideoTrack? = null
    private var micEnabled = true
    private var camEnabled = true
    // 현재 카메라가 전면인지 — 로컬 미리보기 거울과 공유 복귀 핸들에 쓴다(전면만 거울)
    private var frontCamera = true
    private var speakerEnabled = true
    private var started = false
    private var disposed = false
    // 오디오 라우팅 — 통화 모드 진입 전 모드를 기억해 dispose에서 원복한다
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var previousAudioMode = AudioManager.MODE_NORMAL

    private val _localVideo = MutableStateFlow<RtcVideoTrackHandle?>(null)
    override val localVideo: StateFlow<RtcVideoTrackHandle?> = _localVideo.asStateFlow()

    private val _remoteVideos = MutableStateFlow<Map<Long, RtcVideoTrackHandle>>(emptyMap())
    override val remoteVideos: StateFlow<Map<Long, RtcVideoTrackHandle>> = _remoteVideos.asStateFlow()

    private val _screenSharing = MutableStateFlow(false)
    override val screenSharing: StateFlow<Boolean> = _screenSharing.asStateFlow()

    // VM이 수집을 시작한 뒤에야 피어가 생기지만, 순간 폭주(ICE 다발)에 대비해 버퍼를 넉넉히 둔다
    private val _outgoingSignals = MutableSharedFlow<RtcOutgoingSignal>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val outgoingSignals: Flow<RtcOutgoingSignal> = _outgoingSignals.asSharedFlow()

    init {
        initializeFactoryOnce(appContext)
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()
        rtcConfig = PeerConnection.RTCConfiguration(
            iceServers.map { server ->
                PeerConnection.IceServer.builder(server.urls).apply {
                    val username = server.username
                    val credential = server.credential

                    if (!username.isNullOrEmpty() && !credential.isNullOrEmpty()) {
                        setUsername(username)
                        setPassword(credential)
                    }
                }.createIceServer()
            }
        ).apply { sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN }
    }

    override fun start() {
        synchronized(lock) {
            if (disposed || started) return
            started = true

            // 통화 오디오 진입(페이스톡 성격) — 스피커/수화구 라우팅은 통화 모드에서만 확실히 먹는다.
            // dispose가 반드시 원복한다(남기면 앱의 다른 소리까지 통화 경로·볼륨으로 나간다)
            previousAudioMode = audioManager.mode
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            applySpeakerRouting(speakerEnabled)

            val newAudioSource = factory.createAudioSource(MediaConstraints())

            audioSource = newAudioSource
            localAudioTrack = factory.createAudioTrack("audio0", newAudioSource).apply { setEnabled(micEnabled) }

            // 카메라가 없거나 캡처 시작에 실패하면 오디오 전용으로 계속한다(웹 getUserMedia 폴백 미러)
            val capturer = createCameraCapturer() ?: return
            val newVideoSource = factory.createVideoSource(capturer.isScreencast)
            val helper = SurfaceTextureHelper.create("SgRtcCapture", eglBase.eglBaseContext)

            capturer.initialize(helper, appContext, newVideoSource.capturerObserver)
            // 보이스톡(camEnabled=false) 시작이면 카메라를 아직 열지 않는다 — 켤 때 lazy 시작
            // (setCamEnabled). 트랙·sender는 미리 만들어 두므로 켤 때 재협상이 필요 없다
            val captureStarted = !camEnabled ||
                runCatching { capturer.startCapture(CAPTURE_WIDTH, CAPTURE_HEIGHT, CAPTURE_FPS) }.isSuccess

            if (captureStarted) {
                videoSource = newVideoSource
                surfaceHelper = helper
                videoCapturer = capturer
                captureRunning = camEnabled
                val track = factory.createVideoTrack("video0", newVideoSource).apply { setEnabled(camEnabled) }

                localVideoTrack = track
                // 카메라가 실제로 도는 동안만 로컬 핸들 발행 — 보이스톡에선 전환/공유 버튼이 숨는다
                if (captureRunning) {
                    _localVideo.value = AndroidRtcVideoTrackHandle(track, eglBase.eglBaseContext, mirror = frontCamera)
                }
            } else {
                runCatching { capturer.dispose() }
                runCatching { helper.dispose() }
                runCatching { newVideoSource.dispose() }
            }
        }
    }

    override fun createPeer(peerId: Long, initiator: Boolean) {
        val handle = synchronized(lock) {
            if (disposed || peers.containsKey(peerId)) return
            val handle = PeerHandle(peerId)
            val pc = factory.createPeerConnection(rtcConfig, PeerObserver(handle)) ?: return

            handle.pc = pc
            peers[peerId] = handle
            localAudioTrack?.let { pc.addTrack(it) }
            // 공유 중 새로 들어온 피어도 화면을 받는다 — 송출 트랙 자체를 바꿔두는 웹 D9 미러
            (screenVideoTrack ?: localVideoTrack)?.let { pc.addTrack(it) }
            handle
        }

        if (initiator) createAndSendOffer(handle)
    }

    override fun closePeer(peerId: Long) {
        val handle = synchronized(lock) { peers.remove(peerId) } ?: return

        _remoteVideos.update { it - peerId }
        // dispose는 세션 폐기 때 일괄 — 콜백 경합 중 네이티브 해제를 피한다(close만으로 연결은 끊긴다)
        runCatching { handle.pc.close() }
    }

    override fun closeAllPeers() {
        val closing = synchronized(lock) {
            val copy = peers.values.toList()

            peers.clear()
            copy
        }

        _remoteVideos.value = emptyMap()
        closing.forEach { runCatching { it.pc.close() } }
    }

    override fun applySignal(fromUserId: Long, type: RtcSignalType, payload: String) {
        val handle = synchronized(lock) { peers[fromUserId] } ?: return

        when (type) {
            RtcSignalType.OFFER -> applyRemoteOffer(handle, payload)
            RtcSignalType.ANSWER -> applyRemoteAnswer(handle, payload)
            RtcSignalType.ICE -> applyRemoteCandidate(handle, payload)
        }
    }

    override fun setMicEnabled(enabled: Boolean) {
        micEnabled = enabled
        localAudioTrack?.setEnabled(enabled)
    }

    override fun setCamEnabled(enabled: Boolean) {
        var handle: AndroidRtcVideoTrackHandle? = null

        synchronized(lock) {
            camEnabled = enabled
            localVideoTrack?.setEnabled(enabled)
            // 보이스톡 → 페이스톡 전환: 시작 때 열지 않은 카메라를 이때 연다 —
            // sender에는 트랙이 이미 실려 있어 재협상 없이 프레임만 흐르기 시작한다
            val capturer = videoCapturer

            if (enabled && !captureRunning && !disposed && capturer != null) {
                if (runCatching { capturer.startCapture(CAPTURE_WIDTH, CAPTURE_HEIGHT, CAPTURE_FPS) }.isSuccess) {
                    captureRunning = true
                    // 공유 중이면 로컬 표시는 화면 트랙 유지 — 복귀(stopScreenShare) 때 카메라 핸들이 실린다
                    if (screenVideoTrack == null) {
                        handle = localVideoTrack?.let {
                            AndroidRtcVideoTrackHandle(it, eglBase.eglBaseContext, mirror = frontCamera)
                        }
                    }
                }
            }
        }
        handle?.let { _localVideo.value = it }
    }

    override fun switchCamera() {
        val capturer = synchronized(lock) { if (disposed) null else videoCapturer } ?: return

        // 결과(전/후면)는 libwebrtc가 콜백 스레드로 알려준다 — 전면만 거울로 핸들을 재발행하면
        // 렌더러(key(handle))가 새 거울 설정으로 다시 붙는다. 상대에게는 원본이 그대로 간다
        capturer.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFrontCamera: Boolean) {
                val handle = synchronized(lock) {
                    frontCamera = isFrontCamera
                    // 공유 중엔 localVideo가 화면 트랙 — 복귀(stopScreenShare) 때 거울이 반영된다
                    if (disposed || screenVideoTrack != null) null
                    else localVideoTrack?.let { AndroidRtcVideoTrackHandle(it, eglBase.eglBaseContext, mirror = isFrontCamera) }
                }

                handle?.let { _localVideo.value = it }
            }

            override fun onCameraSwitchError(errorDescription: String?) = Unit
        })
    }

    override fun setSpeakerEnabled(enabled: Boolean) {
        speakerEnabled = enabled
        // start() 전이면 기억만 — 통화 모드 진입 시 현재 토글대로 적용된다
        if (started) applySpeakerRouting(enabled)
    }

    /** 켬=본체 스피커 강제, 끔=기본 경로(수화구·이어폰) — API 31+는 communication device 지정 */
    @Suppress("DEPRECATION")
    private fun applySpeakerRouting(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (enabled) {
                audioManager.availableCommunicationDevices
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    ?.let(audioManager::setCommunicationDevice)
            } else {
                audioManager.clearCommunicationDevice()
            }
        } else {
            audioManager.isSpeakerphoneOn = enabled
        }
    }

    override fun startScreenShare(grant: RtcScreenCaptureGrant) {
        val data = (grant as? AndroidRtcScreenCaptureGrant)?.data ?: return

        synchronized(lock) {
            // 오디오 전용이면 video sender가 없어 replaceTrack 불가 — 버튼도 숨겨져 있다(웹 D9)
            if (disposed || screenVideoTrack != null || localVideoTrack == null) return
        }
        // API 29+는 mediaProjection 타입 FGS가 떠 있어야 getMediaProjection이 허용된다 — 서비스 기동 후 캡처
        ScreenShareService.start(appContext) { beginScreenCapture(data) }
    }

    override fun stopScreenShare() {
        var stoppingCapturer: VideoCapturer? = null
        var disposingSource: VideoSource? = null
        var disposingHelper: SurfaceTextureHelper? = null
        var disposingTrack: VideoTrack? = null
        val camHandle = synchronized(lock) {
            val track = screenVideoTrack ?: return
            val camTrack = localVideoTrack

            stoppingCapturer = screenCapturer
            disposingSource = screenSource
            disposingHelper = screenHelper
            disposingTrack = track
            screenCapturer = null
            screenSource = null
            screenHelper = null
            screenVideoTrack = null
            // 보관해둔 카메라 트랙을 각 sender에 복귀 — 재협상 없음(웹 stopScreenShare 미러)
            peers.values.forEach { peer ->
                peer.pc.senders
                    .firstOrNull { it.track()?.kind() == MediaStreamTrack.VIDEO_TRACK_KIND }
                    ?.setTrack(camTrack, false)
            }
            camTrack?.let { AndroidRtcVideoTrackHandle(it, eglBase.eglBaseContext, mirror = frontCamera) }
        }

        _localVideo.value = camHandle
        _screenSharing.value = false
        // 네이티브 해제는 락 밖에서 — stopCapture가 캡처 스레드 완료를 기다린다
        runCatching { stoppingCapturer?.stopCapture() }
        runCatching { stoppingCapturer?.dispose() }
        runCatching { disposingTrack?.dispose() }
        runCatching { disposingSource?.dispose() }
        runCatching { disposingHelper?.dispose() }
        ScreenShareService.stop(appContext)
    }

    /** FGS 기동 완료 후 호출 — MediaProjection을 열고 화면 트랙으로 각 피어 sender를 교체한다(D9) */
    private fun beginScreenCapture(data: Intent) {
        val handle = synchronized(lock) {
            if (disposed || screenVideoTrack != null || localVideoTrack == null) {
                ScreenShareService.stop(appContext)
                return
            }
            val capturer = ScreenCapturerAndroid(data, object : MediaProjection.Callback() {
                // 시스템 UI(상태바)에서 캡처를 끊은 경우 — 웹 track.onended와 같은 정리 경로
                override fun onStop() {
                    stopScreenShare()
                }
            })
            val source = factory.createVideoSource(capturer.isScreencast)
            val helper = SurfaceTextureHelper.create("SgScreenCapture", eglBase.eglBaseContext)

            capturer.initialize(helper, appContext, source.capturerObserver)
            // 화면 원본 비율 유지, 긴 변만 캡처 상한으로 축소 — 모바일 인코더 부하 제한
            val metrics = appContext.resources.displayMetrics
            val scale = (CAPTURE_WIDTH.toFloat() / maxOf(metrics.widthPixels, metrics.heightPixels)).coerceAtMost(1f)
            val captureStarted = runCatching {
                capturer.startCapture(
                    (metrics.widthPixels * scale).toInt(),
                    (metrics.heightPixels * scale).toInt(),
                    SCREEN_CAPTURE_FPS
                )
            }.isSuccess

            if (!captureStarted) {
                runCatching { capturer.dispose() }
                runCatching { helper.dispose() }
                runCatching { source.dispose() }
                ScreenShareService.stop(appContext)
                return
            }
            val track = factory.createVideoTrack("screen0", source)

            screenCapturer = capturer
            screenSource = source
            screenHelper = helper
            screenVideoTrack = track
            // 전송 중인 video sender의 트랙만 교체 — SDP 재협상 없음, 시그널링 무변경(웹 D9)
            peers.values.forEach { peer ->
                peer.pc.senders
                    .firstOrNull { it.track()?.kind() == MediaStreamTrack.VIDEO_TRACK_KIND }
                    ?.setTrack(track, false)
            }
            AndroidRtcVideoTrackHandle(track, eglBase.eglBaseContext, mirror = false)
        }

        _localVideo.value = handle
        _screenSharing.value = true
    }

    override fun dispose() {
        val closing = synchronized(lock) {
            if (disposed) return
            disposed = true
            val copy = peers.values.toList()

            peers.clear()
            copy
        }

        _localVideo.value = null
        _remoteVideos.value = emptyMap()
        _screenSharing.value = false
        closing.forEach {
            runCatching { it.pc.close() }
            runCatching { it.pc.dispose() }
        }
        // 공유 중에 끊으면 화면 캡처(FGS 포함)도 함께 내린다 — 웹 cleanup의 screenTrack.stop 미러
        runCatching { screenCapturer?.stopCapture() }
        runCatching { screenCapturer?.dispose() }
        runCatching { screenVideoTrack?.dispose() }
        runCatching { screenSource?.dispose() }
        runCatching { screenHelper?.dispose() }
        if (screenCapturer != null) ScreenShareService.stop(appContext)
        runCatching { videoCapturer?.stopCapture() }
        runCatching { videoCapturer?.dispose() }
        runCatching { surfaceHelper?.dispose() }
        runCatching { localVideoTrack?.dispose() }
        runCatching { localAudioTrack?.dispose() }
        runCatching { videoSource?.dispose() }
        runCatching { audioSource?.dispose() }
        runCatching { factory.dispose() }
        runCatching { eglBase.release() }
        // 오디오 라우팅 원복 — 통화 모드에 들어간 적이 있을 때만(스피커 강제 해제 후 이전 모드로)
        if (started) {
            applySpeakerRouting(false)
            audioManager.mode = previousAudioMode
        }
    }

    /** offer 생성 → 로컬 SDP 설정 성공 후에만 릴레이(웹이 pc.localDescription을 보내는 것과 동일 시점) */
    private fun createAndSendOffer(handle: PeerHandle) {
        handle.pc.createOffer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(description: SessionDescription?) {
                val offer = description ?: return

                handle.pc.setLocalDescription(object : SdpObserverAdapter() {
                    override fun onSetSuccess() {
                        emitDescription(handle.peerId, RtcSignalType.OFFER, offer)
                    }
                }, offer)
            }
        }, MediaConstraints())
    }

    private fun applyRemoteOffer(handle: PeerHandle, payload: String) {
        val offer = parseDescription(payload) ?: return

        handle.pc.setRemoteDescription(object : SdpObserverAdapter() {
            override fun onSetSuccess() {
                drainPendingCandidates(handle)
                handle.pc.createAnswer(object : SdpObserverAdapter() {
                    override fun onCreateSuccess(description: SessionDescription?) {
                        val answer = description ?: return

                        handle.pc.setLocalDescription(object : SdpObserverAdapter() {
                            override fun onSetSuccess() {
                                emitDescription(handle.peerId, RtcSignalType.ANSWER, answer)
                            }
                        }, answer)
                    }
                }, MediaConstraints())
            }
        }, offer)
    }

    private fun applyRemoteAnswer(handle: PeerHandle, payload: String) {
        val answer = parseDescription(payload) ?: return

        handle.pc.setRemoteDescription(object : SdpObserverAdapter() {
            override fun onSetSuccess() {
                drainPendingCandidates(handle)
            }
        }, answer)
    }

    /** 원격 SDP 설정 전에 도착한 candidate는 큐잉했다가 적용한다(표준 패턴, 웹 pendingCandidates 미러) */
    private fun applyRemoteCandidate(handle: PeerHandle, payload: String) {
        val candidate = parseCandidate(payload) ?: return
        val applyNow = synchronized(lock) {
            if (handle.remoteDescriptionSet) true else {
                handle.pendingCandidates += candidate
                false
            }
        }

        if (applyNow) runCatching { handle.pc.addIceCandidate(candidate) }
    }

    private fun drainPendingCandidates(handle: PeerHandle) {
        val queued = synchronized(lock) {
            handle.remoteDescriptionSet = true
            val copy = handle.pendingCandidates.toList()

            handle.pendingCandidates.clear()
            copy
        }

        queued.forEach { runCatching { handle.pc.addIceCandidate(it) } }
    }

    private fun emitDescription(peerId: Long, type: RtcSignalType, description: SessionDescription) {
        val payload = JSONObject()
            .put("type", description.type.canonicalForm())
            .put("sdp", description.description)
            .toString()

        _outgoingSignals.tryEmit(RtcOutgoingSignal(toUserId = peerId, type = type, payload = payload))
    }

    /** 웹 JSON.stringify(localDescription)와 같은 {type,sdp} 형태를 파싱한다 */
    private fun parseDescription(payload: String): SessionDescription? = runCatching {
        val json = JSONObject(payload)

        SessionDescription(
            SessionDescription.Type.fromCanonicalForm(json.getString("type")),
            json.getString("sdp")
        )
    }.getOrNull()

    /** 웹 JSON.stringify(candidate)와 같은 {candidate,sdpMid,sdpMLineIndex} 형태를 파싱한다 */
    private fun parseCandidate(payload: String): IceCandidate? = runCatching {
        val json = JSONObject(payload)

        IceCandidate(
            json.optString("sdpMid"),
            json.optInt("sdpMLineIndex", 0),
            json.getString("candidate")
        )
    }.getOrNull()

    private fun createCameraCapturer(): CameraVideoCapturer? {
        val enumerator: CameraEnumerator =
            if (Camera2Enumerator.isSupported(appContext)) Camera2Enumerator(appContext) else Camera1Enumerator(true)
        val deviceNames = enumerator.deviceNames
        val deviceName = deviceNames.firstOrNull(enumerator::isFrontFacing) ?: deviceNames.firstOrNull() ?: return null

        // 전면이 없는 기기(후면 시작)면 처음부터 거울 없이 그린다
        frontCamera = enumerator.isFrontFacing(deviceName)
        return enumerator.createCapturer(deviceName, null)
    }

    private class PeerHandle(val peerId: Long) {
        lateinit var pc: PeerConnection
        val pendingCandidates = mutableListOf<IceCandidate>()
        var remoteDescriptionSet = false
    }

    /** libwebrtc 콜백 어댑터 — 시그널링 스레드에서 불리므로 Flow 발행만 하고 상태는 건드리지 않는다 */
    private inner class PeerObserver(private val handle: PeerHandle) : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate?) {
            if (candidate == null) return
            val payload = JSONObject()
                .put("candidate", candidate.sdp)
                .put("sdpMid", candidate.sdpMid)
                .put("sdpMLineIndex", candidate.sdpMLineIndex)
                .toString()

            _outgoingSignals.tryEmit(RtcOutgoingSignal(toUserId = handle.peerId, type = RtcSignalType.ICE, payload = payload))
        }

        override fun onTrack(transceiver: RtpTransceiver?) {
            val track = transceiver?.receiver?.track()

            if (track is VideoTrack) {
                _remoteVideos.update {
                    it + (handle.peerId to AndroidRtcVideoTrackHandle(track, eglBase.eglBaseContext, mirror = false))
                }
            }
        }

        override fun onSignalingChange(newState: PeerConnection.SignalingState?) = Unit
        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) = Unit
        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) = Unit
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
        override fun onAddStream(stream: MediaStream?) = Unit
        override fun onRemoveStream(stream: MediaStream?) = Unit
        override fun onDataChannel(dataChannel: DataChannel?) = Unit
        override fun onRenegotiationNeeded() = Unit
    }

    /** SdpObserver 4메소드 중 필요한 것만 덮는 어댑터 — 실패는 로그 대상이지만 통화를 멈추진 않는다 */
    private abstract class SdpObserverAdapter : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription?) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String?) = Unit
        override fun onSetFailure(error: String?) = Unit
    }

    private companion object {
        const val CAPTURE_WIDTH = 1280
        const val CAPTURE_HEIGHT = 720
        const val CAPTURE_FPS = 30
        // 화면은 정지 화면이 대부분 — 카메라(30)보다 낮춰 인코더 부하를 아낀다
        const val SCREEN_CAPTURE_FPS = 15

        private var factoryInitialized = false

        /** PeerConnectionFactory 전역 초기화는 프로세스당 1회면 충분하다 */
        fun initializeFactoryOnce(appContext: Context) {
            synchronized(this) {
                if (factoryInitialized) return
                factoryInitialized = true
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(appContext).createInitializationOptions()
                )
            }
        }
    }
}
