package kr.hhp227.storygroup.shared.domain.repository

import kotlinx.coroutines.flow.Flow
import kr.hhp227.storygroup.shared.domain.model.IceServer
import kr.hhp227.storygroup.shared.domain.model.RtcCallEvent
import kr.hhp227.storygroup.shared.domain.model.RtcCallPeer
import kr.hhp227.storygroup.shared.domain.model.RtcRoom
import kr.hhp227.storygroup.shared.domain.model.RtcSignalEvent
import kr.hhp227.storygroup.shared.domain.model.RtcSignalType

/**
 * WebRTC 시그널링 데이터 접근 — 그룹 회의(meetings/{id})와 DM 통화(chat-rooms/{id}) 공용.
 * 미디어(P2P)는 플랫폼 네이티브가 담당하고, 여긴 로스터·SDP/ICE 릴레이·벨울림만 나른다(웹 D2 미러).
 */
interface RtcRepository {

    /**
     * 통화 실시간 로스터 구독 — 서버 계약상 rtc 토픽 구독 자체가 통화 입장이다.
     * 수집하는 동안 CONNECTED/PEERS/DISCONNECTED를 흘리고 연결 유실 시 5초 간격 자동 재연결.
     * 종료된 회의는 서버가 구독을 거부하므로(ERROR 프레임) 진행 중일 때만 수집해야 한다.
     */
    fun observeCallEvents(room: RtcRoom): Flow<RtcCallEvent>

    /**
     * SDP/ICE 시그널 채널 구독(/user/queue/rtc, 표적 전달) — 큐 하나가 모든 방을 나르므로
     * 이 방(roomKey) 신호만 걸러 흘린다. 시그널·벨울림 SEND도 이 채널의 세션을 쓰므로
     * 보내려면 반드시 수집 중이어야 한다.
     */
    fun observeSignalEvents(room: RtcRoom): Flow<RtcSignalEvent>

    /**
     * SDP/ICE 시그널 발신 — STOMP SEND(휘발, 실패 무시). 서버는 수신자가 같은 rtc 방에
     * 없으면 조용히 버리므로 상대가 PEERS에 나타난 뒤에 보내야 한다.
     */
    suspend fun sendSignal(room: RtcRoom, type: RtcSignalType, toUserId: Long, payload: String)

    /**
     * 통화 벨울림(휘발) — DM은 상대 1명, 그룹 방은 방 멤버 전원의 개인 알림 큐로 CALL_INVITE가
     * 릴레이된다(페이스톡 미러). 진행 중 통화 합류면 서버가 다시 울리지 않는다.
     * 수락/거절 시그널은 없다 — 건 쪽은 PEERS에 상대가 안 오면 그만(웹 D6 미러).
     * video=false는 보이스톡 — 수신 측 배너 표시·카메라 OFF 입장에 쓰인다.
     */
    suspend fun sendCallInvite(chatRoomId: Long, video: Boolean)

    /** ICE 서버 구성 조회 — 실패 시 호출 측이 STUN 폴백을 쓴다(조회 실패가 통화를 막으면 안 된다) */
    suspend fun getIceServers(): Result<List<IceServer>>

    /**
     * 통화 로스터 스냅숏(REST) — rtc 토픽 구독은 곧 입장이라, 입장 없이 "통화 중 N명"을
     * 미리 보는 읽기 경로(웹 라이브 카드 미러). 채팅방 라이브 바가 주기 폴링한다.
     */
    suspend fun getCallRoster(chatRoomId: Long): Result<List<RtcCallPeer>>
}
