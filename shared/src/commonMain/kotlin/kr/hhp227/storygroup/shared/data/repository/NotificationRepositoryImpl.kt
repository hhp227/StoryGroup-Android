package kr.hhp227.storygroup.shared.data.repository

import app.cash.paging.Pager
import app.cash.paging.PagingData
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import kotlinx.coroutines.flow.Flow
import kr.hhp227.storygroup.shared.data.network.dto.NotificationResponse
import kr.hhp227.storygroup.shared.data.network.dto.UnreadCountResponse
import kr.hhp227.storygroup.shared.data.paging.PagePagingConfig
import kr.hhp227.storygroup.shared.data.paging.PagePagingSource
import kr.hhp227.storygroup.shared.domain.model.AppNotification
import kr.hhp227.storygroup.shared.domain.model.NotificationTargetType
import kr.hhp227.storygroup.shared.domain.model.NotificationType
import kr.hhp227.storygroup.shared.domain.repository.NotificationRepository

class NotificationRepositoryImpl(private val client: HttpClient) : NotificationRepository {

    override fun getNotificationsPagingData(): Flow<PagingData<AppNotification>> =
        Pager(PagePagingConfig) {
            PagePagingSource { page, size ->
                client.get("/api/notifications") {
                    parameter("page", page)
                    parameter("size", size)
                }.body<List<NotificationResponse>>().mapNotNull { it.toDomain() }
            }
        }.flow

    override suspend fun getUnreadCount(): Result<Long> =
        runCatching { client.get("/api/notifications/unread-count").body<UnreadCountResponse>().count }

    override suspend fun markAsRead(notificationId: Long): Result<Unit> =
        runCatching {
            client.patch("/api/notifications/$notificationId/read")
            Unit
        }

    override suspend fun markAllAsRead(): Result<Unit> =
        runCatching {
            client.post("/api/notifications/read-all")
            Unit
        }
}

// 서버가 새 타입을 추가해도 목록 조회가 통째로 깨지지 않게, 모르는 타입은 항목째 걸러낸다
// (표시할 라벨이 없는 알림이라 기본값 흡수 대신 제외가 맞다 — 그룹류 enum 흡수 정책과 구분)
private fun NotificationResponse.toDomain(): AppNotification? {
    val domainType = NotificationType.entries.firstOrNull { it.name == type } ?: return null

    return AppNotification(
        id = id,
        type = domainType,
        targetType = targetType?.let { raw -> NotificationTargetType.entries.firstOrNull { it.name == raw } },
        targetId = targetId,
        isRead = isRead,
        createdAt = createdAt
    )
}
