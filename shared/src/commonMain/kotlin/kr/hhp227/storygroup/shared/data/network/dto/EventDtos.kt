package kr.hhp227.storygroup.shared.data.network.dto

import kotlinx.serialization.Serializable

// StoryGroup-WebApp event/dto/EventDtos.kt 계약과 1:1 (V19, 리비전 00066부터 서빙)

// GET /api/groups/{gid}/events 항목·RSVP/생성 응답 공용
@Serializable
data class EventResponse(
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
    val createdAt: String,
    val goingCount: Long = 0,
    val maybeCount: Long = 0,
    val notGoingCount: Long = 0,
    // 조회자 본인의 RSVP 상태 — null이면 아직 응답 안 함
    val myRsvp: String? = null
)

// GET /api/groups/{gid}/events/{eid} 응답 — 목록 항목 + 참석자 명단
@Serializable
data class EventDetailResponse(
    val event: EventResponse,
    val attendees: List<EventAttendeeResponse> = emptyList()
)

@Serializable
data class EventAttendeeResponse(
    val userId: Long,
    val name: String,
    val profileImg: String? = null,
    val status: String
)

// POST /api/groups/{gid}/events 요청 본문 — title ≤100, description ≤2000, location ≤200
@Serializable
data class CreateEventRequest(
    val title: String,
    val description: String? = null,
    val location: String? = null,
    val startsAt: String,
    val endsAt: String? = null
)

// PUT /api/groups/{gid}/events/{eid}/rsvp 요청 본문
@Serializable
data class RsvpRequest(
    val status: String
)
