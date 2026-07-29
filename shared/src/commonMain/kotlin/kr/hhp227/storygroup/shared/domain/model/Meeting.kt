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

/** 회의 참가 기록(REST) — 실시간 통화 로스터(RtcCallPeer)와 달리 이미 나간 사람도 남는다 */
data class MeetingParticipant(
    val userId: Long,
    val name: String,
    val profileImg: String? = null,
    val joinedAt: String,
    val leftAt: String? = null
) {
    val isActive: Boolean get() = leftAt == null
}
