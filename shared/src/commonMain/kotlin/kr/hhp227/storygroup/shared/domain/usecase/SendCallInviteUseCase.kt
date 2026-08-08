package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.repository.RtcRepository

/**
 * 통화 벨울림 발신(휘발) — DM은 상대 1명, 그룹 방은 방 멤버 전원의 개인 알림 큐로
 * CALL_INVITE가 릴레이된다(페이스톡 미러). 이미 진행 중인 통화에 합류하는 경우는
 * 서버가 다시 울리지 않는다. 수락/거절 시그널은 없다 — 건 쪽은 PEERS에 아무도
 * 안 들어오면 그만(타임아웃 UI, 웹 D6 미러).
 */
class SendCallInviteUseCase(
    private val rtcRepository: RtcRepository
) {
    /** video=false는 보이스톡 — 수신 측 배너 표시·카메라 OFF 입장에 쓰인다 */
    @Throws(Exception::class)
    suspend operator fun invoke(chatRoomId: Long, video: Boolean) {
        rtcRepository.sendCallInvite(chatRoomId, video)
    }
}
