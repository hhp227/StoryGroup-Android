package kr.hhp227.storygroup.shared.data.network.dto

import kotlinx.serialization.Serializable

// StoryGroup-WebApp meeting/dto/MeetingDtos.kt · realtime/RtcSocketEvent.kt 계약과 1:1

/** GET·POST /api/groups/{g}/meetings — 화상회의. endedAt null이면 진행 중 */
@Serializable
data class MeetingResponse(
    val id: Long,
    val groupId: Long,
    val hostId: Long,
    val startedAt: String,
    val endedAt: String? = null
)

/**
 * GET .../meetings/{id}/participants — 참가 기록(나간 사람은 leftAt이 채워진 채 남는다).
 * 이름 필드는 서버 SQL 별칭 그대로 authorName/authorProfileImg(웹 Participant 계약 동일)
 */
@Serializable
data class MeetingParticipantResponse(
    val userId: Long,
    val authorName: String,
    val authorProfileImg: String? = null,
    val joinedAt: String,
    val leftAt: String? = null
)

/** /topic/rtc/... PEERS 봉투의 통화 참여자 항목 */
@Serializable
data class RtcPeerResponse(
    val userId: Long,
    val userName: String
)

/**
 * STOMP /topic/rtc/meetings/{id} 수신 봉투 — 서버 realtime/RtcSocketEvent.kt 1:1.
 * 지금 이 토픽으로 오는 타입은 PEERS뿐이고, peers는 증분이 아니라 항상 전체 목록(수신 측 자가 복구).
 * type은 미지의 값 추가에 대비해 String으로 받고 도메인 매핑에서 걸러낸다(채팅 봉투 정책 미러).
 */
@Serializable
data class RtcTopicEventResponse(
    val type: String,
    val roomKey: String? = null,
    val peers: List<RtcPeerResponse> = emptyList()
)
