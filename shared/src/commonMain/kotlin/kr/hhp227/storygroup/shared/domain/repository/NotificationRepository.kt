package kr.hhp227.storygroup.shared.domain.repository

import app.cash.paging.PagingData
import kotlinx.coroutines.flow.Flow
import kr.hhp227.storygroup.shared.domain.model.AppNotification

interface NotificationRepository {
    /**
     * 알림 Paging 스트림(최신순) — GET /api/notifications?page&size.
     * cachedIn은 각 플랫폼 프레젠테이션 경계에서 적용한다.
     */
    fun getNotificationsPagingData(): Flow<PagingData<AppNotification>>

    /** 미읽음 알림 수 — GET /api/notifications/unread-count */
    suspend fun getUnreadCount(): Result<Long>

    /** 단건 읽음 처리 — PATCH /api/notifications/{id}/read */
    suspend fun markAsRead(notificationId: Long): Result<Unit>

    /** 전체 읽음 처리 — POST /api/notifications/read-all */
    suspend fun markAllAsRead(): Result<Unit>
}
