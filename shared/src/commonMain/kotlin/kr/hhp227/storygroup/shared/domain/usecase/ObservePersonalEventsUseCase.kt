package kr.hhp227.storygroup.shared.domain.usecase

import kotlinx.coroutines.flow.Flow
import kr.hhp227.storygroup.shared.domain.model.PersonalEvent
import kr.hhp227.storygroup.shared.domain.repository.NotificationRepository

/**
 * 개인 큐 실시간 이벤트 구독(STOMP /user/queue/notifications) — 셸 종 뱃지와 채팅 허브 뱃지용.
 * 여러 화면이 동시에 수집해도 소켓은 1개를 공유하며, 전원 수집 취소 시 연결이 닫힌다.
 */
class ObservePersonalEventsUseCase(
    private val notificationRepository: NotificationRepository
) {
    operator fun invoke(): Flow<PersonalEvent> = notificationRepository.observePersonalEvents()
}
