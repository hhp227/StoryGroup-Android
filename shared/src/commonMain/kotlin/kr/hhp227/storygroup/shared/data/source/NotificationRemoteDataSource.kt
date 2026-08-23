package kr.hhp227.storygroup.shared.data.source

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import kotlinx.coroutines.flow.Flow
import kr.hhp227.storygroup.shared.config.AppLinks
import kr.hhp227.storygroup.shared.data.network.StompSessionEvent
import kr.hhp227.storygroup.shared.data.network.StompSocket
import kr.hhp227.storygroup.shared.data.network.dto.NotificationResponse
import kr.hhp227.storygroup.shared.data.network.dto.UnreadCountResponse
import kr.hhp227.storygroup.shared.data.storage.TokenStorage

/**
 * 알림 원격 소스 — 전송·DTO만 담당, 예외는 그대로 던진다(Result 래핑·이벤트 파싱·도메인 매핑은 리포지토리 몫).
 * 개인 큐(STOMP) 소켓을 소유한다 — 구독 Flow는 호출마다 새 연결을 열 수 있다 — 단일 연결 공유는 NotificationRepositoryImpl의 shareIn이 담당한다.
 */
interface NotificationRemoteDataSource {
    /** 개인 큐(/user/queue/notifications) 구독 — 원시 세션 이벤트를 그대로 흘려보낸다 */
    fun subscribePersonalEvents(): Flow<StompSessionEvent>

    suspend fun getNotifications(page: Int, size: Int): List<NotificationResponse>
    suspend fun getUnreadCount(): UnreadCountResponse
    suspend fun markAsRead(notificationId: Long)
    suspend fun markAllAsRead()
}

class NotificationRemoteDataSourceImpl(
    private val client: HttpClient,
    tokenStorage: TokenStorage,
    baseUrl: String = AppLinks.BASE_URL
) : NotificationRemoteDataSource {

    private val socket = StompSocket(client, baseUrl, tokenStorage)

    override fun subscribePersonalEvents(): Flow<StompSessionEvent> =
        socket.subscribe("/user/queue/notifications")

    override suspend fun getNotifications(page: Int, size: Int): List<NotificationResponse> =
        client.get("/api/notifications") {
            parameter("page", page)
            parameter("size", size)
        }.body()

    override suspend fun getUnreadCount(): UnreadCountResponse =
        client.get("/api/notifications/unread-count").body()

    override suspend fun markAsRead(notificationId: Long) {
        client.patch("/api/notifications/$notificationId/read")
    }

    override suspend fun markAllAsRead() {
        client.post("/api/notifications/read-all")
    }
}
