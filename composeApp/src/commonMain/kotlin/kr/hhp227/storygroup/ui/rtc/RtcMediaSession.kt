package kr.hhp227.storygroup.ui.rtc

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kr.hhp227.storygroup.shared.domain.model.IceServer
import kr.hhp227.storygroup.shared.domain.model.RtcSignalType

/** 비디오 트랙 핸들 — 플랫폼 구현이 실제 트랙을 감싼다(commonMain은 불투명, RtcVideoView가 소비) */
interface RtcVideoTrackHandle

/** 화면 캡처 허가 토큰 — Android는 MediaProjection 동의 결과를 감싼다(commonMain은 불투명) */
interface RtcScreenCaptureGrant

/** 미디어 세션이 밖으로 흘리는 시그널 — VM이 STOMP(/app/rtc/.../signal)로 릴레이한다 */
data class RtcOutgoingSignal(
    val toUserId: Long,
    val type: RtcSignalType,
    val payload: String
)

/**
 * 플랫폼 WebRTC 미디어 세션 — 경계를 넘나드는 건 시그널 문자열(SDP/ICE JSON)과 트랙 핸들뿐.
 * 메시 구성 규칙(글레어 방지: userId 작은 쪽이 offer)은 VM이 판단해 createPeer(initiator)로 내린다.
 * 미디어는 README 원칙대로 :shared에 넣지 않는다 — Android=libwebrtc, Desktop=미지원(null 팩토리).
 */
interface RtcMediaSession {

    /** 로컬 캡처 시작 — 카메라 실패 시 오디오 전용으로 계속한다(웹 폴백 미러) */
    fun start()

    /** 로컬 미리보기 트랙 — 카메라가 없거나 실패하면 null(오디오 전용) */
    val localVideo: StateFlow<RtcVideoTrackHandle?>

    /** 원격 비디오 트랙(userId별) — 오디오 전용 상대는 항목이 없다 */
    val remoteVideos: StateFlow<Map<Long, RtcVideoTrackHandle>>

    /** 릴레이할 시그널 스트림 — 통화 중 VM이 수집해 STOMP로 보낸다 */
    val outgoingSignals: Flow<RtcOutgoingSignal>

    /** 피어 연결 생성 — initiator면 offer를 만들어 outgoingSignals로 흘린다. 이미 있으면 무시 */
    fun createPeer(peerId: Long, initiator: Boolean)

    /** 피어 연결 종료(로스터에서 빠진 상대) — 원격 트랙도 함께 정리된다 */
    fun closePeer(peerId: Long)

    /** 시그널링 유실 시 메시 전체 해체(로컬 미디어 유지) — 재연결 후 PEERS로 재구축(웹 D7 미러) */
    fun closeAllPeers()

    /** 수신 시그널 적용 — OFFER면 answer를 만들어 되흘리고, ICE는 원격 SDP 설정 전이면 큐잉한다 */
    fun applySignal(fromUserId: Long, type: RtcSignalType, payload: String)

    fun setMicEnabled(enabled: Boolean)

    fun setCamEnabled(enabled: Boolean)

    /** 전/후면 카메라 전환 — 반대편 카메라가 없으면 무시. 전면만 거울(localVideo 핸들 재발행으로 반영) */
    fun switchCamera()

    /**
     * 스피커폰 라우팅 — 켬=본체 스피커 강제, 끔=기본 경로(수화구·이어폰) 복귀. 영상통화라 기본 ON.
     * 통화 오디오 모드 진입/원복은 start()/dispose() 소관(Desktop은 세션 자체가 없어 자연 no-op).
     */
    fun setSpeakerEnabled(enabled: Boolean)

    /** 화면 공유 중 여부 — 시작/중지·시스템 측 캡처 중단까지 반영한다(웹 sharing 미러) */
    val screenSharing: StateFlow<Boolean>

    /**
     * 화면 공유 시작 — 각 피어 video sender의 트랙만 교체한다(재협상 없음, 웹 D9 미러).
     * 카메라 트랙은 stop하지 않고 보관하고, 오디오 전용(video sender 없음)이면 무시한다.
     */
    fun startScreenShare(grant: RtcScreenCaptureGrant)

    /** 화면 공유 중지 — 보관해둔 카메라 트랙을 각 sender에 복귀시킨다(웹 D9 미러) */
    fun stopScreenShare()

    /** 세션 폐기 — 캡처/피어/팩토리 전부 해제. 이후 어떤 호출도 하면 안 된다 */
    fun dispose()
}

interface RtcMediaSessionFactory {

    /** 이 플랫폼이 미디어를 지원하지 않으면 null(Desktop) — 통화는 로스터 전용으로 동작한다 */
    fun create(iceServers: List<IceServer>): RtcMediaSession?
}

/** 플랫폼 팩토리 획득 — Android는 applicationContext를 캡처하므로 VM에 넘겨도 누수가 없다 */
@Composable
expect fun rememberRtcMediaSessionFactory(): RtcMediaSessionFactory

/**
 * 카메라/마이크 권한 요청 런처 — 반환 람다를 호출하면 (필요 시) 시스템 다이얼로그가 뜨고
 * 결과가 [onResult]로 온다(이미 허용됐으면 즉시 true). Desktop은 권한 개념이 없어 항상 true.
 */
@Composable
expect fun rememberRtcPermissionsRequester(onResult: (granted: Boolean) -> Unit): () -> Unit

/**
 * 화면 캡처 동의 요청 런처 — 반환 람다를 호출하면 Android는 MediaProjection 시스템 다이얼로그가
 * 뜨고 결과가 [onResult]로 온다(거부·미지원 플랫폼은 null — 웹 getDisplayMedia 취소 미러).
 */
@Composable
expect fun rememberRtcScreenCaptureRequester(onResult: (grant: RtcScreenCaptureGrant?) -> Unit): () -> Unit
