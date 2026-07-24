package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.repository.NotificationRepository

/** 미읽음 알림 수 조회 — 알림 화면 헤더(추후 셸 종 아이콘 뱃지)가 소비한다 */
class GetUnreadNotificationCountUseCase(private val notificationRepository: NotificationRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(): Long = notificationRepository.getUnreadCount().getOrThrow()
}
