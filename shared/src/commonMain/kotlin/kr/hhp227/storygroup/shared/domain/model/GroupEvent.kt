package kr.hhp227.storygroup.shared.domain.model

/** 일정 참석 응답 상태 — 서버 event_rsvps.status CHECK와 1:1 */
enum class RsvpStatus { GOING, MAYBE, NOT_GOING }

/**
 * 그룹 일정(GET /api/groups/{id}/events) 도메인 모델 — 시각은 서버 ISO-8601 원문,
 * 로컬 날짜 귀속·표시 포맷팅은 각 플랫폼 UI가 담당. 카운트·myRsvp는 RSVP 응답마다
 * 서버가 집계해 돌려준다(웹 EventCard onChange 미러 — 낙관적 갱신 없음).
 */
data class GroupEvent(
    val id: Long,
    val groupId: Long,
    val userId: Long,
    val authorName: String,
    val authorProfileImg: String? = null,
    val title: String,
    val description: String? = null,
    val location: String? = null,
    val startsAt: String,
    // null = 종료 시각 없는 일정(시작 시각만 공지)
    val endsAt: String? = null,
    val createdAt: String = "",
    val goingCount: Long = 0,
    val maybeCount: Long = 0,
    val notGoingCount: Long = 0,
    // null = 아직 응답 안 함
    val myRsvp: RsvpStatus? = null
)

/** 참석자 명단 항목 — 단건 조회에만 실려 온다(멤버 목록처럼 차단 무관 표시) */
data class EventAttendee(
    val userId: Long,
    val name: String,
    val profileImg: String? = null,
    val status: RsvpStatus
)

/** 단건 조회(GET .../events/{id}) — 목록 항목 + 참석자 명단 */
data class GroupEventDetail(
    val event: GroupEvent,
    val attendees: List<EventAttendee>
)
