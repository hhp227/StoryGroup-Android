package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.GroupEvent
import kr.hhp227.storygroup.shared.domain.repository.EventRepository

/** 월 범위 일정 목록 — 캘린더가 [월초, 다음달 초) ISO를 넘긴다(웹 listEvents 미러) */
class GetGroupEventsUseCase(private val eventRepository: EventRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(groupId: Long, fromIso: String, toIso: String): List<GroupEvent> =
        eventRepository.listEvents(groupId, fromIso, toIso).getOrThrow()
}
