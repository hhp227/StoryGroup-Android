package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.repository.EventRepository

/** 일정 삭제 — 작성자 본인 또는 방장/부방장(서버 검증, 댓글 삭제 규칙과 동일) */
class DeleteEventUseCase(private val eventRepository: EventRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(groupId: Long, eventId: Long) {
        eventRepository.deleteEvent(groupId, eventId).getOrThrow()
    }
}
