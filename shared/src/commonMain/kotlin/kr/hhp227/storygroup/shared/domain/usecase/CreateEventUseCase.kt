package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.GroupEvent
import kr.hhp227.storygroup.shared.domain.repository.EventRepository

/** 일정 생성(멤버 누구나, 작성자 자동 GOING — 서버 정책) */
class CreateEventUseCase(private val eventRepository: EventRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(
        groupId: Long,
        title: String,
        description: String?,
        location: String?,
        startsAtIso: String,
        endsAtIso: String?
    ): GroupEvent =
        eventRepository.createEvent(groupId, title, description, location, startsAtIso, endsAtIso).getOrThrow()
}
