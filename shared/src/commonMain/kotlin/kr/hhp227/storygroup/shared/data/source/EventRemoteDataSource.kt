package kr.hhp227.storygroup.shared.data.source

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
import kr.hhp227.storygroup.shared.data.network.dto.EventDetailResponse
import kr.hhp227.storygroup.shared.data.network.dto.EventResponse
import kr.hhp227.storygroup.shared.data.network.dto.RsvpRequest

/** 일정 원격 소스 — 전송·DTO만 담당, 예외는 그대로 던진다(Result 래핑·도메인 매핑은 리포지토리 몫) */
interface EventRemoteDataSource {
    suspend fun listEvents(groupId: Long, fromIso: String, toIso: String): List<EventResponse>
    suspend fun getEvent(groupId: Long, eventId: Long): EventDetailResponse
    suspend fun createEvent(
        groupId: Long,
        title: String,
        description: String?,
        location: String?,
        startsAtIso: String,
        endsAtIso: String?
    ): EventResponse
    suspend fun deleteEvent(groupId: Long, eventId: Long)
    suspend fun rsvp(groupId: Long, eventId: Long, status: String): EventResponse
    suspend fun cancelRsvp(groupId: Long, eventId: Long): EventResponse
}

class EventRemoteDataSourceImpl(private val client: HttpClient) : EventRemoteDataSource {
    override suspend fun listEvents(groupId: Long, fromIso: String, toIso: String): List<EventResponse> =
        client.get("/api/groups/$groupId/events") {
            parameter("from", fromIso)
            parameter("to", toIso)
        }.body()

    override suspend fun getEvent(groupId: Long, eventId: Long): EventDetailResponse =
        client.get("/api/groups/$groupId/events/$eventId").body()

    override suspend fun createEvent(
        groupId: Long,
        title: String,
        description: String?,
        location: String?,
        startsAtIso: String,
        endsAtIso: String?
    ): EventResponse =
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
        }.body()

    override suspend fun deleteEvent(groupId: Long, eventId: Long) {
        client.delete("/api/groups/$groupId/events/$eventId")
    }

    override suspend fun rsvp(groupId: Long, eventId: Long, status: String): EventResponse =
        client.put("/api/groups/$groupId/events/$eventId/rsvp") {
            contentType(ContentType.Application.Json)
            setBody(RsvpRequest(status = status))
        }.body()

    override suspend fun cancelRsvp(groupId: Long, eventId: Long): EventResponse =
        client.delete("/api/groups/$groupId/events/$eventId/rsvp").body()
}
