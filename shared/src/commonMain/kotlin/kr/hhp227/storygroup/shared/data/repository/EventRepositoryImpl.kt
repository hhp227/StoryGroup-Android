package kr.hhp227.storygroup.shared.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kr.hhp227.storygroup.shared.data.network.dto.CreateEventRequest
import kr.hhp227.storygroup.shared.data.network.dto.EventAttendeeResponse
import kr.hhp227.storygroup.shared.data.network.dto.EventDetailResponse
import kr.hhp227.storygroup.shared.data.network.dto.EventResponse
import kr.hhp227.storygroup.shared.data.network.dto.RsvpRequest
import kr.hhp227.storygroup.shared.domain.model.EventAttendee
import kr.hhp227.storygroup.shared.domain.model.GroupEvent
import kr.hhp227.storygroup.shared.domain.model.GroupEventDetail
import kr.hhp227.storygroup.shared.domain.model.RsvpStatus
import kr.hhp227.storygroup.shared.domain.repository.EventRepository

class EventRepositoryImpl(private val client: HttpClient) : EventRepository {

    override suspend fun listEvents(groupId: Long, fromIso: String, toIso: String): Result<List<GroupEvent>> =
        runCatching {
            client.get("/api/groups/$groupId/events") {
                parameter("from", fromIso)
                parameter("to", toIso)
            }.body<List<EventResponse>>().map { it.toDomain() }
        }

    override suspend fun getEvent(groupId: Long, eventId: Long): Result<GroupEventDetail> =
        runCatching {
            val response = client.get("/api/groups/$groupId/events/$eventId").body<EventDetailResponse>()

            GroupEventDetail(
                event = response.event.toDomain(),
                attendees = response.attendees.map { it.toDomain() }
            )
        }

    override suspend fun createEvent(
        groupId: Long,
        title: String,
        description: String?,
        location: String?,
        startsAtIso: String,
        endsAtIso: String?
    ): Result<GroupEvent> = runCatching {
        client.post("/api/groups/$groupId/events") {
            contentType(ContentType.Application.Json)
            setBody(
                CreateEventRequest(
                    title = title,
                    description = description,
                    location = location,
                    startsAt = startsAtIso,
                    endsAt = endsAtIso
                )
            )
        }.body<EventResponse>().toDomain()
    }

    override suspend fun deleteEvent(groupId: Long, eventId: Long): Result<Unit> =
        runCatching {
            client.delete("/api/groups/$groupId/events/$eventId")
            Unit
        }

    override suspend fun rsvp(groupId: Long, eventId: Long, status: RsvpStatus): Result<GroupEvent> =
        runCatching {
            client.put("/api/groups/$groupId/events/$eventId/rsvp") {
                contentType(ContentType.Application.Json)
                setBody(RsvpRequest(status = status.name))
            }.body<EventResponse>().toDomain()
        }

    override suspend fun cancelRsvp(groupId: Long, eventId: Long): Result<GroupEvent> =
        runCatching {
            client.delete("/api/groups/$groupId/events/$eventId/rsvp").body<EventResponse>().toDomain()
        }
}

private fun EventResponse.toDomain() = GroupEvent(
    id = id,
    groupId = groupId,
    userId = userId,
    authorName = authorName,
    authorProfileImg = authorProfileImg,
    title = title,
    description = description,
    location = location,
    startsAt = startsAt,
    endsAt = endsAt,
    createdAt = createdAt,
    goingCount = goingCount,
    maybeCount = maybeCount,
    notGoingCount = notGoingCount,
    // 미지의 값은 미응답으로 흡수 — 서버가 상태를 늘려도 목록이 통째로 깨지지 않는다
    myRsvp = myRsvp?.let { raw -> RsvpStatus.entries.firstOrNull { it.name == raw } }
)

private fun EventAttendeeResponse.toDomain() = EventAttendee(
    userId = userId,
    name = name,
    profileImg = profileImg,
    status = RsvpStatus.entries.firstOrNull { it.name == status } ?: RsvpStatus.GOING
)
