package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.repository.MeetingRepository

/** 회의 종료 — 호스트 전용(403은 서버 문구로 실패). 남은 참가 기록은 서버가 일괄 leave 처리 */
class EndMeetingUseCase(
    private val meetingRepository: MeetingRepository
) {
    @Throws(Exception::class)
    suspend operator fun invoke(groupId: Long, meetingId: Long) {
        meetingRepository.endMeeting(groupId, meetingId).getOrThrow()
    }
}
