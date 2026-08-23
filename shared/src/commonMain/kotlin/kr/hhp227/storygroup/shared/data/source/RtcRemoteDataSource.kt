package kr.hhp227.storygroup.shared.data.source

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.flow.Flow
import kr.hhp227.storygroup.shared.config.AppLinks
import kr.hhp227.storygroup.shared.data.network.StompSessionEvent
import kr.hhp227.storygroup.shared.data.network.StompSocket
import kr.hhp227.storygroup.shared.data.network.dto.IceServersResponse
import kr.hhp227.storygroup.shared.data.network.dto.RtcPeerResponse
import kr.hhp227.storygroup.shared.data.storage.TokenStorage

/**
 * WebRTC 시그널링 원격 소스 — 전송·DTO만 담당, 예외는 그대로 던진다(Result 래핑·이벤트 파싱·도메인 매핑은 리포지토리 몫).
 * 로스터 토픽 소켓과 시그널 채널 소켓을 분리 소유한다(StompSocket은 목적지당 세션 1개 전제).
 */
interface RtcRemoteDataSource {
    /** 로스터 토픽(/topic/rtc/{roomKey}) 구독 — 원시 세션 이벤트를 그대로 흘려보낸다 */
    fun subscribeTopic(roomKey: String): Flow<StompSessionEvent>

    /** 시그널 채널(/user/queue/rtc) 구독 — 원시 세션 이벤트를 그대로 흘려보낸다 */
    fun subscribeSignal(): Flow<StompSessionEvent>

    /** SDP/ICE 시그널 SEND(휘발) — body는 리포지토리가 인코딩한 JSON. signalSocket의 살아있는 세션을 빌려 쓴다 */
    suspend fun sendSignal(roomKey: String, body: String)

    /** 통화 벨울림 SEND(휘발) — body는 리포지토리가 인코딩한 JSON */
    suspend fun sendCallInvite(chatRoomId: Long, body: String)

    suspend fun getIceServers(): IceServersResponse
    suspend fun getCallRoster(chatRoomId: Long): List<RtcPeerResponse>
}

class RtcRemoteDataSourceImpl(
    private val client: HttpClient,
    tokenStorage: TokenStorage,
    baseUrl: String = AppLinks.BASE_URL
) : RtcRemoteDataSource {

    // 로스터 토픽용 소켓 — StompSocket은 목적지당 세션 1개 전제라 시그널 채널과 분리한다
    private val topicSocket = StompSocket(client, baseUrl, tokenStorage)

    // 시그널 채널 전용 소켓 — 시그널·벨울림 SEND(trySend)는 이 소켓의 살아있는 세션을 빌려 쓴다
    // (subscribeSignal 수집 중에만 유효)
    private val signalSocket = StompSocket(client, baseUrl, tokenStorage)

    override fun subscribeTopic(roomKey: String): Flow<StompSessionEvent> =
        topicSocket.subscribe("/topic/rtc/$roomKey")

    override fun subscribeSignal(): Flow<StompSessionEvent> =
        signalSocket.subscribe("/user/queue/rtc")

    override suspend fun sendSignal(roomKey: String, body: String) {
        signalSocket.trySend("/app/rtc/$roomKey/signal", body)
    }

    override suspend fun sendCallInvite(chatRoomId: Long, body: String) {
        signalSocket.trySend("/app/rtc/chat-rooms/$chatRoomId/invite", body)
    }

    override suspend fun getIceServers(): IceServersResponse =
        client.get("/api/rtc/ice-servers").body()

    override suspend fun getCallRoster(chatRoomId: Long): List<RtcPeerResponse> =
        client.get("/api/rtc/chat-rooms/$chatRoomId/roster").body()
}
