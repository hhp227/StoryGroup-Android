package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.GroupEvent
import kr.hhp227.storygroup.shared.domain.repository.EventRepository

/** RSVP 취소 — 같은 상태 버튼 재탭이 취소다(웹 handleRsvp 토글 미러) */
class CancelEventRsvpUseCase(private val eventRepository: EventRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(groupId: Long, eventId: Long): GroupEvent =
        eventRepository.cancelRsvp(groupId, eventId).getOrThrow()
}
