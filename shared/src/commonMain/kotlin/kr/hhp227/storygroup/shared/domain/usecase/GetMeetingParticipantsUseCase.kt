package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.MeetingParticipant
import kr.hhp227.storygroup.shared.domain.repository.MeetingRepository

/** 회의 참가 기록 전체 조회(참여순, 나간 사람 포함) — 호스트 이름 표시도 이 목록에서 파생한다 */
class GetMeetingParticipantsUseCase(
    private val meetingRepository: MeetingRepository
) {
    @Throws(Exception::class)
    suspend operator fun invoke(groupId: Long, meetingId: Long): List<MeetingParticipant> =
        meetingRepository.getParticipants(groupId, meetingId).getOrThrow()
}
