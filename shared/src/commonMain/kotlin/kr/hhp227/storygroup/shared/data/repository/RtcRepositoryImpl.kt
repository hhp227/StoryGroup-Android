package kr.hhp227.storygroup.shared.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kr.hhp227.storygroup.shared.data.network.StompSessionEvent
import kr.hhp227.storygroup.shared.data.network.dto.CallInviteRequest
import kr.hhp227.storygroup.shared.data.network.dto.RtcSignalEventResponse
import kr.hhp227.storygroup.shared.data.network.dto.RtcSignalRequest
import kr.hhp227.storygroup.shared.data.network.dto.RtcTopicEventResponse
import kr.hhp227.storygroup.shared.data.source.RtcRemoteDataSource
import kr.hhp227.storygroup.shared.domain.model.IceServer
import kr.hhp227.storygroup.shared.domain.model.RtcCallEvent
import kr.hhp227.storygroup.shared.domain.model.RtcCallEventType
import kr.hhp227.storygroup.shared.domain.model.RtcCallPeer
import kr.hhp227.storygroup.shared.domain.model.RtcRoom
import kr.hhp227.storygroup.shared.domain.model.RtcRoomKind
import kr.hhp227.storygroup.shared.domain.model.RtcSignalEvent
import kr.hhp227.storygroup.shared.domain.model.RtcSignalEventType
import kr.hhp227.storygroup.shared.domain.model.RtcSignalType
import kr.hhp227.storygroup.shared.domain.repository.RtcRepository

class RtcRepositoryImpl(
    private val rtcRemoteDataSource: RtcRemoteDataSource
) : RtcRepository {

    // STOMP 프레임 본문 디코드/인코드용 — ApiClient의 ContentNegotiation 설정과 동일 정책
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    override fun observeCallEvents(room: RtcRoom): Flow<RtcCallEvent> =
        rtcRemoteDataSource.subscribeTopic(room.roomKey).mapNotNull { event ->
            when (event) {
                StompSessionEvent.Connected -> RtcCallEvent(RtcCallEventType.CONNECTED)
                StompSessionEvent.Disconnected -> RtcCallEvent(RtcCallEventType.DISCONNECTED)
                is StompSessionEvent.Message ->
                    runCatching { json.decodeFromString<RtcTopicEventResponse>(event.body) }
                        .getOrNull()?.toDomain()
            }
        }

    override fun observeSignalEvents(room: RtcRoom): Flow<RtcSignalEvent> =
        rtcRemoteDataSource.subscribeSignal().mapNotNull { event ->
            when (event) {
                StompSessionEvent.Connected -> RtcSignalEvent(RtcSignalEventType.CONNECTED)
                StompSessionEvent.Disconnected -> RtcSignalEvent(RtcSignalEventType.DISCONNECTED)
                is StompSessionEvent.Message ->
                    runCatching { json.decodeFromString<RtcSignalEventResponse>(event.body) }
                        .getOrNull()?.toDomain(room)
            }
        }

    override suspend fun sendSignal(room: RtcRoom, type: RtcSignalType, toUserId: Long, payload: String) {
        val body = json.encodeToString(RtcSignalRequest(type = type.name, toUserId = toUserId, payload = payload))

        rtcRemoteDataSource.sendSignal(room.roomKey, body)
    }

    override suspend fun sendCallInvite(chatRoomId: Long, video: Boolean) {
        rtcRemoteDataSource.sendCallInvite(chatRoomId, json.encodeToString(CallInviteRequest(video = video)))
    }

    override suspend fun getIceServers(): Result<List<IceServer>> =
        runCatching {
            rtcRemoteDataSource.getIceServers().iceServers.map {
                IceServer(urls = it.urls, username = it.username, credential = it.credential)
            }
        }

    override suspend fun getCallRoster(chatRoomId: Long): Result<List<RtcCallPeer>> =
        runCatching {
            rtcRemoteDataSource.getCallRoster(chatRoomId).map { RtcCallPeer(userId = it.userId, userName = it.userName) }
        }
}

/** 서버 roomKey 문자열(meetings/5, chat-rooms/7) — destination 조립과 수신 필터에 함께 쓴다 */
private val RtcRoom.roomKey: String
    get() = when (kind) {
        RtcRoomKind.MEETING -> "meetings/$id"
        RtcRoomKind.DIRECT -> "chat-rooms/$id"
    }

/** 모르는 봉투 타입은 항목째 제외 — 후속 타입이 같은 토픽에 추가돼도 안전 */
private fun RtcTopicEventResponse.toDomain(): RtcCallEvent? = when (type) {
    "PEERS" -> RtcCallEvent(
        type = RtcCallEventType.PEERS,
        peers = peers.map { RtcCallPeer(userId = it.userId, userName = it.userName) }
    )
    else -> null
}

/** 다른 방 신호(roomKey 불일치)와 모르는 타입은 항목째 제외 — 개인 큐 공유 계약의 수신 측 필터 */
private fun RtcSignalEventResponse.toDomain(room: RtcRoom): RtcSignalEvent? {
    if (roomKey != room.roomKey) return null
    val signalType = RtcSignalType.entries.firstOrNull { it.name == type } ?: return null
    val from = fromUserId ?: return null

    return RtcSignalEvent(
        type = RtcSignalEventType.SIGNAL,
        signalType = signalType,
        fromUserId = from,
        fromUserName = fromUserName,
        payload = payload ?: return null
    )
}
