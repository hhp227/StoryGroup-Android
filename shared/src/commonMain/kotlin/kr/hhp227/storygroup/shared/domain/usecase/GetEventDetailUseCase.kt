package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.GroupEventDetail
import kr.hhp227.storygroup.shared.domain.repository.EventRepository

/** 일정 단건+참석자 명단 — 카드의 참석자 펼침이 lazy 조회한다(웹 toggleAttendees 미러) */
class GetEventDetailUseCase(private val eventRepository: EventRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(groupId: Long, eventId: Long): GroupEventDetail =
        eventRepository.getEvent(groupId, eventId).getOrThrow()
}
