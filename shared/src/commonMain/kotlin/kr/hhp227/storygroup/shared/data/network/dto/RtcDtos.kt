package kr.hhp227.storygroup.shared.data.network.dto

import kotlinx.serialization.Serializable

// StoryGroup-WebApp realtime/RtcSocketEvent.kt 계약과 1:1

/** /topic/rtc/... PEERS 봉투의 통화 참여자 항목 */
@Serializable
data class RtcPeerResponse(
    val userId: Long,
    val userName: String
)

/**
 * STOMP /topic/rtc/chat-rooms/{id} 수신 봉투 — 서버 realtime/RtcSocketEvent.kt 1:1.
 * 지금 이 토픽으로 오는 타입은 PEERS뿐이고, peers는 증분이 아니라 항상 전체 목록(수신 측 자가 복구).
 * type은 미지의 값 추가에 대비해 String으로 받고 도메인 매핑에서 걸러낸다(채팅 봉투 정책 미러).
 */
@Serializable
data class RtcTopicEventResponse(
    val type: String,
    val roomKey: String? = null,
    val peers: List<RtcPeerResponse> = emptyList()
)

/** 클라 → /app/rtc/chat-rooms/{id}/signal SEND 바디 — 서버 RtcSignalRequest 1:1. payload는 불투명 JSON 문자열 */
@Serializable
data class RtcSignalRequest(
    val type: String,
    val toUserId: Long,
    val payload: String
)

/**
 * STOMP /user/queue/rtc 수신 봉투 — 서버 RtcSignalEvent 1:1(type은 OFFER|ANSWER|ICE).
 * 개인 큐 하나가 모든 rtc 방의 신호를 나르므로 수신 측이 roomKey로 걸러야 한다.
 */
@Serializable
data class RtcSignalEventResponse(
    val type: String,
    val roomKey: String? = null,
    val fromUserId: Long? = null,
    val fromUserName: String? = null,
    val payload: String? = null
)

/** GET /api/rtc/ice-servers 항목 — 필드명이 브라우저 RTCIceServer와 동일. STUN 항목은 username/credential null */
@Serializable
data class IceServerResponse(
    val urls: List<String>,
    val username: String? = null,
    val credential: String? = null
)

/** GET /api/rtc/ice-servers 응답 — 지금 배포는 STUN 1개, TURN은 서버 env만 채우면 항목이 추가된다 */
@Serializable
data class IceServersResponse(
    val iceServers: List<IceServerResponse> = emptyList()
)
