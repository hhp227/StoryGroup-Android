package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.repository.RtcRepository

/**
 * DM 벨울림 발신(휘발) — 상대의 개인 알림 큐로 CALL_INVITE가 릴레이된다.
 * 수락/거절 시그널은 없다 — 건 쪽은 PEERS에 상대가 안 들어오면 그만(타임아웃 UI, 웹 D6 미러).
 */
class SendDmCallInviteUseCase(
    private val rtcRepository: RtcRepository
) {
    suspend operator fun invoke(chatRoomId: Long) {
        rtcRepository.sendCallInvite(chatRoomId)
    }
}
