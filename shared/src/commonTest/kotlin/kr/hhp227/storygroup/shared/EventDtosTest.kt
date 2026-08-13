package kr.hhp227.storygroup.shared

import kotlinx.serialization.json.Json
import kr.hhp227.storygroup.shared.data.network.dto.EventDetailResponse
import kr.hhp227.storygroup.shared.data.network.dto.EventResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EventDtosTest {
    private val json = Json { ignoreUnknownKeys = true }

    // 서버 EventResponse(V19) 전체 필드 — myRsvp null=미응답, endsAt null=종료 시각 없는 일정
    @Test
    fun decodesEventResponse() {
        val decoded = json.decodeFromString<EventResponse>(
            """{"id":1,"groupId":31,"userId":3,"authorName":"홍","authorProfileImg":null,
               |"title":"정기 모임","description":"장소 미정","location":"강남",
               |"startsAt":"2026-08-15T19:00:00+09:00","endsAt":null,
               |"createdAt":"2026-08-12T00:00:00Z","goingCount":2,"maybeCount":1,
               |"notGoingCount":0,"myRsvp":"GOING"}""".trimMargin().replace("\n", "")
        )
        assertEquals(1L, decoded.id)
        assertEquals("GOING", decoded.myRsvp)
        assertNull(decoded.endsAt)
        assertEquals(2L, decoded.goingCount)
    }

    @Test
    fun decodesDetailWithAttendees() {
        val decoded = json.decodeFromString<EventDetailResponse>(
            """{"event":{"id":1,"groupId":31,"userId":3,"authorName":"홍","title":"모임",
               |"startsAt":"2026-08-15T19:00:00+09:00","createdAt":"2026-08-12T00:00:00Z",
               |"goingCount":1,"maybeCount":0,"notGoingCount":0,"myRsvp":null},
               |"attendees":[{"userId":3,"name":"홍","profileImg":null,"status":"GOING"}]}"""
                .trimMargin().replace("\n", "")
        )
        assertEquals(1, decoded.attendees.size)
        assertEquals("GOING", decoded.attendees[0].status)
        assertNull(decoded.event.myRsvp)
    }
}
