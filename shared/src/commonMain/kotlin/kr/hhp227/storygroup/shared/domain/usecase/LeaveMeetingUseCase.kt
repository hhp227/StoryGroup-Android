package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.repository.MeetingRepository

/** 회의 참가 기록 종료(멱등) — 참가한 적이 없어도 성공한다 */
class LeaveMeetingUseCase(
    private val meetingRepository: MeetingRepository
) {
    @Throws(Exception::class)
    suspend operator fun invoke(groupId: Long, meetingId: Long) {
        meetingRepository.leaveMeeting(groupId, meetingId).getOrThrow()
    }
}
