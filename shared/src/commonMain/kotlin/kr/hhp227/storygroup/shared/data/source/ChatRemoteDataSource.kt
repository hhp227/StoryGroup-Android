package kr.hhp227.storygroup.shared.data.source

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.Flow
import kr.hhp227.storygroup.shared.config.AppLinks
import kr.hhp227.storygroup.shared.data.network.StompSessionEvent
import kr.hhp227.storygroup.shared.data.network.StompSocket
import kr.hhp227.storygroup.shared.data.network.dto.ChatRoomResponse
import kr.hhp227.storygroup.shared.data.network.dto.CreateMessageRequest
import kr.hhp227.storygroup.shared.data.network.dto.DirectRoomResponse
import kr.hhp227.storygroup.shared.data.network.dto.GroupChatRoomResponse
import kr.hhp227.storygroup.shared.data.network.dto.MarkChatReadRequest
import kr.hhp227.storygroup.shared.data.network.dto.MessageAttachmentPayload
import kr.hhp227.storygroup.shared.data.network.dto.MessageResponse
import kr.hhp227.storygroup.shared.data.network.dto.ReadPositionResponse
import kr.hhp227.storygroup.shared.data.storage.TokenStorage

/**
 * 채팅 원격 소스 — 전송·DTO만 담당, 예외는 그대로 던진다(Result 래핑·이벤트 파싱·도메인 매핑은 리포지토리 몫).
 * 채팅방 실시간(STOMP) 소켓을 소유한다(화면당 구독 1개 — 채팅방 VM 수명 전제, StompSocket 계약 미러).
 */
interface ChatRemoteDataSource {
    /** 채팅방 토픽(/topic/chat-rooms/{chatRoomId}) 구독 — 원시 세션 이벤트를 그대로 흘려보낸다 */
    fun subscribeRoom(chatRoomId: Long): Flow<StompSessionEvent>

    /** 타이핑 신호 SEND(휘발) — 살아있는 세션이 없으면 조용히 버린다(StompSocket.trySend 가드 미러) */
    suspend fun sendTyping(chatRoomId: Long)

    suspend fun getGroupChatRooms(): List<GroupChatRoomResponse>
    suspend fun getDirectRooms(): List<DirectRoomResponse>
    suspend fun getMessages(groupId: Long?, chatRoomId: Long, page: Int, size: Int): List<MessageResponse>
    suspend fun sendMessage(groupId: Long?, chatRoomId: Long, text: String, attachment: MessageAttachmentPayload?): MessageResponse
    suspend fun markRead(groupId: Long?, chatRoomId: Long, lastReadMessageId: Long)
    suspend fun getReadPositions(groupId: Long?, chatRoomId: Long): List<ReadPositionResponse>
    suspend fun openDirectRoom(otherUserId: Long): ChatRoomResponse
    suspend fun getGroupDefaultChatRoom(groupId: Long): List<ChatRoomResponse>
}

class ChatRemoteDataSourceImpl(
    private val client: HttpClient,
    tokenStorage: TokenStorage,
    baseUrl: String = AppLinks.BASE_URL
) : ChatRemoteDataSource {

    private val socket = StompSocket(client, baseUrl, tokenStorage)

    override fun subscribeRoom(chatRoomId: Long): Flow<StompSessionEvent> =
        socket.subscribe("/topic/chat-rooms/$chatRoomId")

    override suspend fun sendTyping(chatRoomId: Long) {
        socket.trySend("/app/chat-rooms/$chatRoomId/typing")
    }

    override suspend fun getGroupChatRooms(): List<GroupChatRoomResponse> =
        client.get("/api/chat-rooms").body()

    override suspend fun getDirectRooms(): List<DirectRoomResponse> =
        client.get("/api/dm").body()

    override suspend fun getMessages(groupId: Long?, chatRoomId: Long, page: Int, size: Int): List<MessageResponse> =
        client.get("${roomPath(groupId, chatRoomId)}/messages") {
            parameter("page", page)
            parameter("size", size)
        }.body()

    override suspend fun sendMessage(
        groupId: Long?,
        chatRoomId: Long,
        text: String,
        attachment: MessageAttachmentPayload?
    ): MessageResponse =
        client.post("${roomPath(groupId, chatRoomId)}/messages") {
            contentType(ContentType.Application.Json)
            setBody(CreateMessageRequest(text, attachment))
        }.body()

    override suspend fun markRead(groupId: Long?, chatRoomId: Long, lastReadMessageId: Long) {
        client.put("${roomPath(groupId, chatRoomId)}/read") {
            contentType(ContentType.Application.Json)
            setBody(MarkChatReadRequest(lastReadMessageId))
        }
    }

    override suspend fun getReadPositions(groupId: Long?, chatRoomId: Long): List<ReadPositionResponse> =
        client.get("${roomPath(groupId, chatRoomId)}/reads").body()

    override suspend fun openDirectRoom(otherUserId: Long): ChatRoomResponse =
        client.post("/api/dm/$otherUserId").body()

    override suspend fun getGroupDefaultChatRoom(groupId: Long): List<ChatRoomResponse> =
        client.get("/api/groups/$groupId/chat-rooms").body()

    /** 그룹 방은 그룹 경로, DM은 /api/dm — 두 계열은 메시지/읽음 하위 경로 형태가 동일하다 */
    private fun roomPath(groupId: Long?, chatRoomId: Long): String =
        if (groupId != null) "/api/groups/$groupId/chat-rooms/$chatRoomId" else "/api/dm/$chatRoomId"
}
