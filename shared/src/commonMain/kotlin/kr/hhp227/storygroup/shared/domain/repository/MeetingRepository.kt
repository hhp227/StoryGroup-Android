package kr.hhp227.storygroup.shared.domain.repository

import app.cash.paging.PagingData
import kotlinx.coroutines.flow.Flow
import kr.hhp227.storygroup.shared.domain.model.IceServer
import kr.hhp227.storygroup.shared.domain.model.Meeting
import kr.hhp227.storygroup.shared.domain.model.MeetingCallEvent
import kr.hhp227.storygroup.shared.domain.model.MeetingParticipant
import kr.hhp227.storygroup.shared.domain.model.MeetingRtcSignalEvent
import kr.hhp227.storygroup.shared.domain.model.MeetingRtcSignalType

/**
 * 그룹 화상회의 데이터 접근 — REST는 참가 "기록"(DB), rtc 토픽은 지금 통화에 "있는" 로스터를 담당한다.
 * 두 축은 느슨 결합: 통화 입장은 rtc 토픽 구독 자체이고, REST join/leave는 기록만 남긴다(웹 미러).
 */
interface MeetingRepository {

    /** 회의 시작 — 바디 없는 POST. 서버가 생성자를 참가자로 자동 등록하고 그룹원에게 MEETING_STARTED 알림 */
    suspend fun createMeeting(groupId: Long): Result<Meeting>

    /** 그룹 회의 목록 Paging 스트림 — 시작 시각 내림차순(서버 정렬) */
    fun getMeetingsPagingData(groupId: Long): Flow<PagingData<Meeting>>

    suspend fun getMeeting(groupId: Long, meetingId: Long): Result<Meeting>

    /** 참가 기록 등록(멱등) — 이미 종료된 회의면 400(서버 문구를 그대로 올린다) */
    suspend fun joinMeeting(groupId: Long, meetingId: Long): Result<Unit>

    /** 참가 기록 종료(멱등) — 참가한 적이 없어도 204 */
    suspend fun leaveMeeting(groupId: Long, meetingId: Long): Result<Unit>

    /** 회의 종료 — 호스트 전용(403). 서버가 남은 참가 기록을 일괄 leave 처리한다 */
    suspend fun endMeeting(groupId: Long, meetingId: Long): Result<Unit>

    /** 참가 기록 전체(참여순, 나간 사람 포함) — 페이징 없음 */
    suspend fun getParticipants(groupId: Long, meetingId: Long): Result<List<MeetingParticipant>>

    /**
     * 통화 실시간 로스터 구독 — 서버 계약상 rtc 토픽 구독 자체가 통화 입장이다.
     * 수집하는 동안 CONNECTED/PEERS/DISCONNECTED를 흘리고 연결 유실 시 5초 간격 자동 재연결.
     * 종료된 회의는 서버가 구독을 거부하므로(ERROR 프레임) 화면은 진행 중일 때만 수집해야 한다.
     */
    fun observeCallEvents(meetingId: Long): Flow<MeetingCallEvent>

    /**
     * SDP/ICE 시그널 채널 구독(/user/queue/rtc, 표적 전달) — 큐 하나가 모든 방을 나르므로
     * 이 회의(roomKey=meetings/{id}) 신호만 걸러 흘린다. 시그널 SEND도 이 채널의 세션을 쓰므로
     * 신호를 보내려면 반드시 수집 중이어야 한다.
     */
    fun observeSignalEvents(meetingId: Long): Flow<MeetingRtcSignalEvent>

    /**
     * SDP/ICE 시그널 발신 — STOMP SEND(휘발, 실패 무시). 서버는 수신자가 같은 rtc 방에
     * 없으면 조용히 버리므로 상대가 PEERS에 나타난 뒤에 보내야 한다.
     */
    suspend fun sendSignal(meetingId: Long, type: MeetingRtcSignalType, toUserId: Long, payload: String)

    /** ICE 서버 구성 조회 — 실패 시 호출 측이 STUN 폴백을 쓴다(조회 실패가 통화를 막으면 안 된다) */
    suspend fun getIceServers(): Result<List<IceServer>>
}
