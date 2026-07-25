package kr.hhp227.storygroup.shared.domain.repository

import kotlinx.coroutines.flow.Flow
import kr.hhp227.storygroup.shared.domain.model.ChatEvent
import kr.hhp227.storygroup.shared.domain.model.ChatMessage
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

    /** 전송은 REST — 서버가 저장 후 STOMP MESSAGE_CREATED를 브로드캐스트한다(클라 STOMP SEND는 서버가 거부) */
    suspend fun sendMessage(groupId: Long?, chatRoomId: Long, text: String): Result<ChatMessage>

    /** 읽음 위치 보고 — 서버가 GREATEST로 단조 증가 보장, READ 이벤트를 방에 브로드캐스트 */
    suspend fun markRead(groupId: Long?, chatRoomId: Long, lastReadMessageId: Long): Result<Unit>

    /**
     * 채팅방 실시간 이벤트 구독 — 수집하는 동안 CONNECTED/서버 이벤트/DISCONNECTED를 흘리고
     * 연결 유실 시 5초 간격으로 자동 재연결한다. 화면은 CONNECTED/DISCONNECTED에서 이력을
     * 재조회해 끊김 공백을 메꾼다(웹 refresh-on-reconnect 미러).
     */
    fun observeRoomEvents(chatRoomId: Long): Flow<ChatEvent>
}
