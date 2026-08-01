package kr.hhp227.storygroup.shared.domain.repository

import app.cash.paging.PagingData
import kotlinx.coroutines.flow.Flow
import kr.hhp227.storygroup.shared.domain.model.AppNotification
import kr.hhp227.storygroup.shared.domain.model.PersonalEvent

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

    /**
     * 개인 큐(STOMP /user/queue/notifications) 실시간 이벤트 — 셸 종 뱃지·채팅 허브 뱃지용.
     * 구독자가 여럿이어도 소켓 연결은 1개를 공유하고, 마지막 수집 취소 시 연결도 닫힌다.
     */
    fun observePersonalEvents(): Flow<PersonalEvent>
}
