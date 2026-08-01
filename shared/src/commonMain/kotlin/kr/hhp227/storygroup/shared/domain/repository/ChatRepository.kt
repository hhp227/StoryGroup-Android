package kr.hhp227.storygroup.shared.domain.repository

import kotlinx.coroutines.flow.Flow
import kr.hhp227.storygroup.shared.domain.model.ChatAttachment
import kr.hhp227.storygroup.shared.domain.model.ChatEvent
import kr.hhp227.storygroup.shared.domain.model.ChatMessage
import kr.hhp227.storygroup.shared.domain.model.ChatReadPosition
import kr.hhp227.storygroup.shared.domain.model.DirectRoom
import kr.hhp227.storygroup.shared.domain.model.GroupChatRoom

/**
 * 채팅 허브/채팅방 데이터 접근.
 * 그룹 채팅방 REST는 groupId가 경로에 필요하고 DM은 방 id만으로 접근한다 —
 * groupId 파라미터가 null이면 DM 경로(/api/dm/...)를 탄다.
 */
interface ChatRepository {

    /** 내 그룹 채팅방 전체(라운지 제외, 서버가 그룹명→방 생성순 정렬) */
    suspend fun getGroupChatRooms(): Result<List<GroupChatRoom>>

    /** 내 DM 방 전체 */
    suspend fun getDirectRooms(): Result<List<DirectRoom>>

    /** 메시지 이력 — 최신순(DESC) 오프셋 페이징, page 0이 가장 최근이고 size는 서버가 50으로 제한 */
    suspend fun getMessages(groupId: Long?, chatRoomId: Long, page: Int, size: Int): Result<List<ChatMessage>>

    /**
     * 전송은 REST — 서버가 저장 후 STOMP MESSAGE_CREATED를 브로드캐스트한다
     * (클라 STOMP SEND는 타이핑 신호만 화이트리스트). text와 attachment 둘 다 비면 서버가 400.
     */
    suspend fun sendMessage(
        groupId: Long?,
        chatRoomId: Long,
        text: String,
        attachment: ChatAttachment? = null
    ): Result<ChatMessage>

    /** 읽음 위치 보고 — 서버가 GREATEST로 단조 증가 보장, READ 이벤트를 방에 브로드캐스트 */
    suspend fun markRead(groupId: Long?, chatRoomId: Long, lastReadMessageId: Long): Result<Unit>

    /** 방 멤버별 마지막 읽음 위치(본인 포함) — "읽음 N" 파생용 초기 스냅숏, 이후는 READ 이벤트로 갱신 */
    suspend fun getReadPositions(groupId: Long?, chatRoomId: Long): Result<List<ChatReadPosition>>

    /**
     * 타이핑 신호 — 유일하게 STOMP SEND로 나가는 휘발 신호.
     * 살아있는 세션이 없으면 조용히 버린다(실패도 무시 — 웹 client.connected 가드 미러).
     */
    suspend fun sendTyping(chatRoomId: Long)

    /** 1:1 DM 방 get-or-create(멱등) — 방 id를 돌려준다. 자기 자신은 400, 차단 관계는 403(BLOCKED) */
    suspend fun openDirectRoom(otherUserId: Long): Result<Long>

    /**
     * 그룹 기본 채팅방 id — 그룹 생성 시 자동으로 만들어져 항상 가장 먼저 생성된 방이다
     * (웹 /groups/[id]/chat의 기본 선택 미러). 방이 하나도 없으면 null.
     */
    suspend fun getGroupDefaultChatRoom(groupId: Long): Result<Long?>

    /**
     * 채팅방 실시간 이벤트 구독 — 수집하는 동안 CONNECTED/서버 이벤트/DISCONNECTED를 흘리고
     * 연결 유실 시 5초 간격으로 자동 재연결한다. 화면은 CONNECTED/DISCONNECTED에서 이력을
     * 재조회해 끊김 공백을 메꾼다(웹 refresh-on-reconnect 미러).
     */
    fun observeRoomEvents(chatRoomId: Long): Flow<ChatEvent>
}
