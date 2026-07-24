package kr.hhp227.storygroup.shared.domain.usecase

import app.cash.paging.PagingData
import kotlinx.coroutines.flow.Flow
import kr.hhp227.storygroup.shared.domain.model.AppNotification
import kr.hhp227.storygroup.shared.domain.repository.NotificationRepository

/**
 * 알림 목록 Paging 스트림(최신순) — cachedIn 없이 반환하고 각 플랫폼 프레젠테이션
 * 경계에서 캐시한다(Android=viewModelScope, iOS=구독 스코프 — docs/KMP.md 규약)
 */
class GetNotificationsPagingDataUseCase(private val notificationRepository: NotificationRepository) {
    operator fun invoke(): Flow<PagingData<AppNotification>> = notificationRepository.getNotificationsPagingData()
}
