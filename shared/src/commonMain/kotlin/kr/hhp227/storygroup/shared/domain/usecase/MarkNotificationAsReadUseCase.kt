package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.repository.NotificationRepository

/** 알림 단건 읽음 처리 — 소유자 불일치/미존재면 서버가 404를 돌려준다 */
class MarkNotificationAsReadUseCase(private val notificationRepository: NotificationRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(notificationId: Long) {
        notificationRepository.markAsRead(notificationId).getOrThrow()
    }
}
