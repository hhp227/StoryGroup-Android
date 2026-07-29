package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.Meeting
import kr.hhp227.storygroup.shared.domain.repository.MeetingRepository

/** 회의 단건 조회 — 진행 여부(endedAt)와 호스트 판정의 기준 스냅숏 */
class GetMeetingUseCase(
    private val meetingRepository: MeetingRepository
) {
    @Throws(Exception::class)
    suspend operator fun invoke(groupId: Long, meetingId: Long): Meeting =
        meetingRepository.getMeeting(groupId, meetingId).getOrThrow()
}
