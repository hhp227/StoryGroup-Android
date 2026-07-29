package kr.hhp227.storygroup.shared.domain.model

/** 그룹 화상회의 — endedAt null이면 진행 중. 시각은 ISO-8601 원문, 표시 포맷팅은 각 플랫폼 UI가 담당 */
data class Meeting(
    val id: Long,
    val groupId: Long,
    val hostId: Long,
    val startedAt: String,
    val endedAt: String? = null
) {
    val isActive: Boolean get() = endedAt == null
}

/** 회의 참가 기록(REST) — 실시간 통화 로스터(MeetingCallPeer)와 달리 이미 나간 사람도 남는다 */
data class MeetingParticipant(
    val userId: Long,
    val name: String,
    val profileImg: String? = null,
    val joinedAt: String,
    val leftAt: String? = null
) {
    val isActive: Boolean get() = leftAt == null
}

/**
 * 통화 실시간 이벤트 종류 — 서버 rtc 토픽 봉투 타입 미러에 클라 합성 2종을 더한 것.
 * CONNECTED/DISCONNECTED는 채팅 ChatEventType과 같은 규칙: 소켓 세션 수립/유실 시 클라가 만들어
 * 흘려, 화면이 REST 재조회(끊김 공백 메꿈)를 트리거하게 한다.
 */
enum class MeetingCallEventType {
    CONNECTED, DISCONNECTED, PEERS
}

/** rtc 토픽 통화 참여자 — 서버 인메모리 로스터 기준(구독 = 통화 입장, 본인 포함) */
data class MeetingCallPeer(
    val userId: Long,
    val userName: String
)

/** 통화 실시간 이벤트 — PEERS는 증분이 아니라 항상 전체 목록이라 수신 측이 자가 복구된다 */
data class MeetingCallEvent(
    val type: MeetingCallEventType,
    val peers: List<MeetingCallPeer> = emptyList()
)

/** WebRTC 시그널 종류 — 서버 RtcSignalType 미러. payload는 서버가 파싱하지 않는 불투명 JSON 문자열 */
enum class MeetingRtcSignalType {
    OFFER, ANSWER, ICE
}

/** 시그널 채널(/user/queue/rtc) 이벤트 종류 — CONNECTED/DISCONNECTED는 클라 합성(채팅과 같은 규칙) */
enum class MeetingRtcSignalEventType {
    CONNECTED, DISCONNECTED, SIGNAL
}

/** 시그널 채널 실시간 이벤트 — SIGNAL일 때만 signalType/fromUserId/payload가 채워진다 */
data class MeetingRtcSignalEvent(
    val type: MeetingRtcSignalEventType,
    val signalType: MeetingRtcSignalType? = null,
    val fromUserId: Long? = null,
    val fromUserName: String? = null,
    val payload: String? = null
)

/** ICE 서버 구성 — 필드가 브라우저 RTCIceServer와 동일해 그대로 PeerConnection에 넣는다 */
data class IceServer(
    val urls: List<String>,
    val username: String? = null,
    val credential: String? = null
)
