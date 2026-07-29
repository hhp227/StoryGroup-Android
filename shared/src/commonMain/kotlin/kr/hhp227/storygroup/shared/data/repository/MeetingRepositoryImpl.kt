package kr.hhp227.storygroup.shared.data.repository

import app.cash.paging.Pager
import app.cash.paging.PagingData
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.json.Json
import kr.hhp227.storygroup.shared.data.network.StompSessionEvent
import kr.hhp227.storygroup.shared.data.network.StompSocket
import kr.hhp227.storygroup.shared.data.network.StoryGroupApi
import kr.hhp227.storygroup.shared.data.network.dto.ErrorResponse
import kr.hhp227.storygroup.shared.data.network.dto.MeetingParticipantResponse
import kr.hhp227.storygroup.shared.data.network.dto.MeetingResponse
import kr.hhp227.storygroup.shared.data.network.dto.RtcTopicEventResponse
import kr.hhp227.storygroup.shared.data.paging.PagePagingConfig
import kr.hhp227.storygroup.shared.data.paging.PagePagingSource
import kr.hhp227.storygroup.shared.data.storage.TokenStorage
import kr.hhp227.storygroup.shared.domain.model.Meeting
import kr.hhp227.storygroup.shared.domain.model.MeetingCallEvent
import kr.hhp227.storygroup.shared.domain.model.MeetingCallEventType
import kr.hhp227.storygroup.shared.domain.model.MeetingCallPeer
import kr.hhp227.storygroup.shared.domain.model.MeetingParticipant
import kr.hhp227.storygroup.shared.domain.repository.MeetingRepository

class MeetingRepositoryImpl(
    private val client: HttpClient,
    tokenStorage: TokenStorage,
    baseUrl: String = StoryGroupApi.DEFAULT_BASE_URL
) : MeetingRepository {

    private val socket = StompSocket(client, baseUrl, tokenStorage)

    // STOMP 프레임 본문 디코드용 — ApiClient의 ContentNegotiation 설정과 동일 정책
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    override suspend fun createMeeting(groupId: Long): Result<Meeting> =
        runCatching {
            client.post("/api/groups/$groupId/meetings").body<MeetingResponse>().toDomain()
        }

    override fun getMeetingsPagingData(groupId: Long): Flow<PagingData<Meeting>> =
        Pager(PagePagingConfig) {
            PagePagingSource { page, size ->
                client.get("/api/groups/$groupId/meetings") {
                    parameter("page", page)
                    parameter("size", size)
                }.body<List<MeetingResponse>>().map { it.toDomain() }
            }
        }.flow

    override suspend fun getMeeting(groupId: Long, meetingId: Long): Result<Meeting> =
        runCatching {
            client.get("/api/groups/$groupId/meetings/$meetingId").body<MeetingResponse>().toDomain()
        }

    override suspend fun joinMeeting(groupId: Long, meetingId: Long): Result<Unit> =
        runCatching {
            try {
                client.post("/api/groups/$groupId/meetings/$meetingId/join")
                Unit
            } catch (e: ClientRequestException) {
                // 화면이 보던 사이 회의가 끝난 경우가 일상 실패 경로(400) — Ktor 예외 원문 대신
                // 서버 에러 본문의 사용자 문구("이미 종료된 회의입니다")를 그대로 보여준다
                val message = runCatching { e.response.body<ErrorResponse>().message }.getOrNull()
                throw IllegalStateException(message ?: "통화에 참가하지 못했습니다.", e)
            }
        }

    override suspend fun leaveMeeting(groupId: Long, meetingId: Long): Result<Unit> =
        runCatching {
            client.post("/api/groups/$groupId/meetings/$meetingId/leave")
            Unit
        }

    override suspend fun endMeeting(groupId: Long, meetingId: Long): Result<Unit> =
        runCatching {
            try {
                client.post("/api/groups/$groupId/meetings/$meetingId/end")
                Unit
            } catch (e: ClientRequestException) {
                // 호스트 아님(403) 등 — 서버 문구를 그대로 올린다
                val message = runCatching { e.response.body<ErrorResponse>().message }.getOrNull()
                throw IllegalStateException(message ?: "회의를 종료하지 못했습니다.", e)
            }
        }

    override suspend fun getParticipants(groupId: Long, meetingId: Long): Result<List<MeetingParticipant>> =
        runCatching {
            client.get("/api/groups/$groupId/meetings/$meetingId/participants")
                .body<List<MeetingParticipantResponse>>()
                .map { it.toDomain() }
        }

    override fun observeCallEvents(meetingId: Long): Flow<MeetingCallEvent> =
        socket.subscribe("/topic/rtc/meetings/$meetingId").mapNotNull { event ->
            when (event) {
                StompSessionEvent.Connected -> MeetingCallEvent(MeetingCallEventType.CONNECTED)
                StompSessionEvent.Disconnected -> MeetingCallEvent(MeetingCallEventType.DISCONNECTED)
                is StompSessionEvent.Message ->
                    runCatching { json.decodeFromString<RtcTopicEventResponse>(event.body) }
                        .getOrNull()?.toDomain()
            }
        }
}

private fun MeetingResponse.toDomain() = Meeting(
    id = id,
    groupId = groupId,
    hostId = hostId,
    startedAt = startedAt,
    endedAt = endedAt
)

private fun MeetingParticipantResponse.toDomain() = MeetingParticipant(
    userId = userId,
    name = authorName,
    profileImg = authorProfileImg,
    joinedAt = joinedAt,
    leftAt = leftAt
)

/** 모르는 봉투 타입은 항목째 제외 — 후속(OFFER/ANSWER/ICE 등)이 같은 토픽에 추가돼도 안전 */
private fun RtcTopicEventResponse.toDomain(): MeetingCallEvent? = when (type) {
    "PEERS" -> MeetingCallEvent(
        type = MeetingCallEventType.PEERS,
        peers = peers.map { MeetingCallPeer(userId = it.userId, userName = it.userName) }
    )
    else -> null
}
