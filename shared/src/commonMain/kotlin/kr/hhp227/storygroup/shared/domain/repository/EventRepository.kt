package kr.hhp227.storygroup.shared.domain.repository

import kr.hhp227.storygroup.shared.domain.model.GroupEvent
import kr.hhp227.storygroup.shared.domain.model.GroupEventDetail
import kr.hhp227.storygroup.shared.domain.model.RsvpStatus

interface EventRepository {
    /** 월 범위 일정 목록 — GET /api/groups/{id}/events?from=&to= (starts_at 기준 [from, to)) */
    suspend fun listEvents(groupId: Long, fromIso: String, toIso: String): Result<List<GroupEvent>>

    /** 일정 단건+참석자 명단 — GET /api/groups/{id}/events/{eventId} */
    suspend fun getEvent(groupId: Long, eventId: Long): Result<GroupEventDetail>

    /** 일정 생성(멤버 누구나, 작성자 자동 GOING) — POST /api/groups/{id}/events */
    suspend fun createEvent(
        groupId: Long,
        title: String,
        description: String?,
        location: String?,
        startsAtIso: String,
        endsAtIso: String?
    ): Result<GroupEvent>

    /** 일정 삭제(작성자/방장/부방장) — DELETE /api/groups/{id}/events/{eventId} */
    suspend fun deleteEvent(groupId: Long, eventId: Long): Result<Unit>

    /** RSVP 응답(upsert) — PUT .../rsvp, 응답은 집계 갱신된 일정 */
    suspend fun rsvp(groupId: Long, eventId: Long, status: RsvpStatus): Result<GroupEvent>

    /** RSVP 취소 — DELETE .../rsvp, 응답은 집계 갱신된 일정 */
    suspend fun cancelRsvp(groupId: Long, eventId: Long): Result<GroupEvent>
}
