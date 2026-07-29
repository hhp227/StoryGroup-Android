package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.Meeting
import kr.hhp227.storygroup.shared.domain.repository.MeetingRepository

/** 회의 시작 — 서버가 생성자를 참가자로 자동 등록하고 그룹원에게 MEETING_STARTED 알림을 보낸다 */
class CreateMeetingUseCase(
    private val meetingRepository: MeetingRepository
) {
    @Throws(Exception::class)
    suspend operator fun invoke(groupId: Long): Meeting =
        meetingRepository.createMeeting(groupId).getOrThrow()
}
