package kr.hhp227.storygroup.shared.data.repository

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
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.json.Json
import kr.hhp227.storygroup.shared.data.network.StompSessionEvent
import kr.hhp227.storygroup.shared.data.network.StompSocket
import kr.hhp227.storygroup.shared.data.network.StoryGroupApi
import kr.hhp227.storygroup.shared.data.network.dto.ChatRoomResponse
import kr.hhp227.storygroup.shared.data.network.dto.ChatSocketEventResponse
import kr.hhp227.storygroup.shared.data.network.dto.CreateMessageRequest
import kr.hhp227.storygroup.shared.data.network.dto.DirectRoomResponse
import kr.hhp227.storygroup.shared.data.network.dto.GroupChatRoomResponse
import kr.hhp227.storygroup.shared.data.network.dto.MarkChatReadRequest
import kr.hhp227.storygroup.shared.data.network.dto.MessageAttachmentPayload
import kr.hhp227.storygroup.shared.data.network.dto.MessageAttachmentResponse
import kr.hhp227.storygroup.shared.data.network.dto.MessageResponse
import kr.hhp227.storygroup.shared.data.network.dto.ReadPositionResponse
import kr.hhp227.storygroup.shared.data.storage.TokenStorage
import kr.hhp227.storygroup.shared.domain.model.ChatAttachment
import kr.hhp227.storygroup.shared.domain.model.ChatEvent
import kr.hhp227.storygroup.shared.domain.model.ChatEventType
import kr.hhp227.storygroup.shared.domain.model.ChatMessage
import kr.hhp227.storygroup.shared.domain.model.ChatReadPosition
import kr.hhp227.storygroup.shared.domain.model.DirectRoom
import kr.hhp227.storygroup.shared.domain.model.GroupChatRoom
import kr.hhp227.storygroup.shared.domain.repository.ChatRepository

class ChatRepositoryImpl(
    private val client: HttpClient,
    tokenStorage: TokenStorage,
    baseUrl: String = StoryGroupApi.DEFAULT_BASE_URL
) : ChatRepository {

    private val socket = StompSocket(client, baseUrl, tokenStorage)

    // STOMP 프레임 본문 디코드용 — ApiClient의 ContentNegotiation 설정과 동일 정책
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    override suspend fun getGroupChatRooms(): Result<List<GroupChatRoom>> =
        runCatching {
            client.get("/api/chat-rooms").body<List<GroupChatRoomResponse>>().map { it.toDomain() }
        }

    override suspend fun getDirectRooms(): Result<List<DirectRoom>> =
        runCatching {
            client.get("/api/dm").body<List<DirectRoomResponse>>().map { it.toDomain() }
        }

    override suspend fun getMessages(
        groupId: Long?,
        chatRoomId: Long,
        page: Int,
        size: Int
    ): Result<List<ChatMessage>> = runCatching {
        client.get("${roomPath(groupId, chatRoomId)}/messages") {
            parameter("page", page)
            parameter("size", size)
        }.body<List<MessageResponse>>().map { it.toDomain() }
    }

    override suspend fun sendMessage(
        groupId: Long?,
        chatRoomId: Long,
        text: String,
        attachment: ChatAttachment?
    ): Result<ChatMessage> = runCatching {
        client.post("${roomPath(groupId, chatRoomId)}/messages") {
            contentType(ContentType.Application.Json)
            setBody(CreateMessageRequest(text, attachment?.toPayload()))
        }.body<MessageResponse>().toDomain()
    }

    override suspend fun markRead(
        groupId: Long?,
        chatRoomId: Long,
        lastReadMessageId: Long
    ): Result<Unit> = runCatching {
        client.put("${roomPath(groupId, chatRoomId)}/read") {
            contentType(ContentType.Application.Json)
            setBody(MarkChatReadRequest(lastReadMessageId))
        }
        Unit
    }

    override suspend fun getReadPositions(
        groupId: Long?,
        chatRoomId: Long
    ): Result<List<ChatReadPosition>> = runCatching {
        client.get("${roomPath(groupId, chatRoomId)}/reads")
            .body<List<ReadPositionResponse>>().map { it.toDomain() }
    }

    override suspend fun sendTyping(chatRoomId: Long) {
        socket.trySend("/app/chat-rooms/$chatRoomId/typing")
    }

    override suspend fun openDirectRoom(otherUserId: Long): Result<Long> =
        runCatching {
            client.post("/api/dm/$otherUserId").body<ChatRoomResponse>().id
        }

    override fun observeRoomEvents(chatRoomId: Long): Flow<ChatEvent> =
        socket.subscribe("/topic/chat-rooms/$chatRoomId").mapNotNull { event ->
            when (event) {
                StompSessionEvent.Connected -> ChatEvent(ChatEventType.CONNECTED, chatRoomId)
                StompSessionEvent.Disconnected -> ChatEvent(ChatEventType.DISCONNECTED, chatRoomId)
                is StompSessionEvent.Message ->
                    runCatching { json.decodeFromString<ChatSocketEventResponse>(event.body) }
                        .getOrNull()?.toDomain()
            }
        }

    /** 그룹 방은 그룹 경로, DM은 /api/dm — 두 계열은 메시지/읽음 하위 경로 형태가 동일하다 */
    private fun roomPath(groupId: Long?, chatRoomId: Long): String =
        if (groupId != null) "/api/groups/$groupId/chat-rooms/$chatRoomId" else "/api/dm/$chatRoomId"
}

private fun GroupChatRoomResponse.toDomain() = GroupChatRoom(
    id = id,
    groupId = groupId,
    groupName = groupName,
    name = name,
    createdAt = createdAt
)

private fun DirectRoomResponse.toDomain() = DirectRoom(
    id = id,
    otherUserId = otherUserId,
    otherUserName = otherUserName,
    otherUserProfileImg = otherUserProfileImg,
    createdAt = createdAt
)

private fun MessageAttachmentResponse.toDomain() = ChatAttachment(
    url = url,
    name = name,
    contentType = contentType,
    size = size
)

private fun ChatAttachment.toPayload() = MessageAttachmentPayload(
    url = url,
    name = name,
    contentType = contentType,
    size = size
)

private fun ReadPositionResponse.toDomain() = ChatReadPosition(
    userId = userId,
    lastReadMessageId = lastReadMessageId
)

private fun MessageResponse.toDomain() = ChatMessage(
    id = id,
    chatRoomId = chatRoomId,
    userId = userId,
    authorName = authorName,
    authorProfileImg = authorProfileImg,
    text = text,
    attachment = attachment?.toDomain(),
    createdAt = createdAt
)

/** 모르는 이벤트 타입은 항목째 제외(알림 mapNotNull 정책 미러) — CONNECTED/DISCONNECTED는 클라 합성이라 서버 wire에 없다 */
private fun ChatSocketEventResponse.toDomain(): ChatEvent? {
    val eventType = ChatEventType.entries.firstOrNull { it.name == type } ?: return null
    return ChatEvent(
        type = eventType,
        chatRoomId = chatRoomId,
        message = message?.toDomain(),
        messageId = messageId,
        userId = userId,
        userName = userName
    )
}
