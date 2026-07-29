package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.repository.MeetingRepository

/** 회의 참가 기록 등록(멱등) — 이미 종료된 회의면 서버 문구("이미 종료된 회의입니다")로 실패한다 */
class JoinMeetingUseCase(
    private val meetingRepository: MeetingRepository
) {
    @Throws(Exception::class)
    suspend operator fun invoke(groupId: Long, meetingId: Long) {
        meetingRepository.joinMeeting(groupId, meetingId).getOrThrow()
    }
}
