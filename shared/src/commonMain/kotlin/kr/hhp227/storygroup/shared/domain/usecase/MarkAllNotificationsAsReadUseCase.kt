package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.repository.NotificationRepository

/** 알림 전체 읽음 처리 — 성공 후 화면이 목록을 새로고침해 서버 상태를 다시 읽는다 */
class MarkAllNotificationsAsReadUseCase(private val notificationRepository: NotificationRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke() {
        notificationRepository.markAllAsRead().getOrThrow()
    }
}
