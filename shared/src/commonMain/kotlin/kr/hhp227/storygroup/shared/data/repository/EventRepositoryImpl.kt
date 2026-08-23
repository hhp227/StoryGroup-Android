package kr.hhp227.storygroup.shared.data.repository

import kr.hhp227.storygroup.shared.data.network.dto.EventAttendeeResponse
import kr.hhp227.storygroup.shared.data.network.dto.EventResponse
import kr.hhp227.storygroup.shared.data.source.EventRemoteDataSource
import kr.hhp227.storygroup.shared.domain.model.EventAttendee
import kr.hhp227.storygroup.shared.domain.model.GroupEvent
import kr.hhp227.storygroup.shared.domain.model.GroupEventDetail
import kr.hhp227.storygroup.shared.domain.model.RsvpStatus
import kr.hhp227.storygroup.shared.domain.repository.EventRepository

class EventRepositoryImpl(private val eventRemoteDataSource: EventRemoteDataSource) : EventRepository {

    override suspend fun listEvents(groupId: Long, fromIso: String, toIso: String): Result<List<GroupEvent>> =
        runCatching {
            eventRemoteDataSource.listEvents(groupId, fromIso, toIso).map { it.toDomain() }
        }

    override suspend fun getEvent(groupId: Long, eventId: Long): Result<GroupEventDetail> =
        runCatching {
            val response = eventRemoteDataSource.getEvent(groupId, eventId)

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
        eventRemoteDataSource.createEvent(
            groupId = groupId,
            title = title,
            description = description,
            location = location,
            startsAtIso = startsAtIso,
            endsAtIso = endsAtIso
        ).toDomain()
    }

    override suspend fun deleteEvent(groupId: Long, eventId: Long): Result<Unit> =
        runCatching {
            eventRemoteDataSource.deleteEvent(groupId, eventId)
        }

    override suspend fun rsvp(groupId: Long, eventId: Long, status: RsvpStatus): Result<GroupEvent> =
        runCatching {
            eventRemoteDataSource.rsvp(groupId, eventId, status.name).toDomain()
        }

    override suspend fun cancelRsvp(groupId: Long, eventId: Long): Result<GroupEvent> =
        runCatching {
            eventRemoteDataSource.cancelRsvp(groupId, eventId).toDomain()
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
