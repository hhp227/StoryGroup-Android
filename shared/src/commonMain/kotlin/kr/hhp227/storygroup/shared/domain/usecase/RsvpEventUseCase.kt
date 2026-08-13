package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.GroupEvent
import kr.hhp227.storygroup.shared.domain.model.RsvpStatus
import kr.hhp227.storygroup.shared.domain.repository.EventRepository

/** RSVP 응답(upsert) — 반환된 일정으로 카드를 교체한다(집계 갱신 포함, 낙관적 갱신 없음) */
class RsvpEventUseCase(private val eventRepository: EventRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(groupId: Long, eventId: Long, status: RsvpStatus): GroupEvent =
        eventRepository.rsvp(groupId, eventId, status).getOrThrow()
}
