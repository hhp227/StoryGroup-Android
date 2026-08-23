package kr.hhp227.storygroup.shared.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.json.Json
import kr.hhp227.storygroup.shared.data.network.StompSessionEvent
import kr.hhp227.storygroup.shared.data.network.dto.ChatSocketEventResponse
import kr.hhp227.storygroup.shared.data.network.dto.DirectRoomResponse
import kr.hhp227.storygroup.shared.data.network.dto.GroupChatRoomResponse
import kr.hhp227.storygroup.shared.data.network.dto.MessageAttachmentPayload
import kr.hhp227.storygroup.shared.data.network.dto.MessageAttachmentResponse
import kr.hhp227.storygroup.shared.data.network.dto.MessageResponse
import kr.hhp227.storygroup.shared.data.network.dto.ReadPositionResponse
import kr.hhp227.storygroup.shared.data.source.ChatRemoteDataSource
import kr.hhp227.storygroup.shared.domain.model.ChatAttachment
import kr.hhp227.storygroup.shared.domain.model.ChatEvent
import kr.hhp227.storygroup.shared.domain.model.ChatEventType
import kr.hhp227.storygroup.shared.domain.model.ChatMessage
import kr.hhp227.storygroup.shared.domain.model.ChatReadPosition
import kr.hhp227.storygroup.shared.domain.model.DirectRoom
import kr.hhp227.storygroup.shared.domain.model.GroupChatRoom
import kr.hhp227.storygroup.shared.domain.repository.ChatRepository

class ChatRepositoryImpl(
    private val chatRemoteDataSource: ChatRemoteDataSource
) : ChatRepository {

    // STOMP 프레임 본문 디코드용 — ApiClient의 ContentNegotiation 설정과 동일 정책
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    override suspend fun getGroupChatRooms(): Result<List<GroupChatRoom>> =
        runCatching {
            chatRemoteDataSource.getGroupChatRooms().map { it.toDomain() }
        }

    override suspend fun getDirectRooms(): Result<List<DirectRoom>> =
        runCatching {
            chatRemoteDataSource.getDirectRooms().map { it.toDomain() }
        }

    override suspend fun getMessages(
        groupId: Long?,
        chatRoomId: Long,
        page: Int,
        size: Int
    ): Result<List<ChatMessage>> = runCatching {
        chatRemoteDataSource.getMessages(groupId, chatRoomId, page, size).map { it.toDomain() }
    }

    override suspend fun sendMessage(
        groupId: Long?,
        chatRoomId: Long,
        text: String,
        attachment: ChatAttachment?
    ): Result<ChatMessage> = runCatching {
        chatRemoteDataSource.sendMessage(groupId, chatRoomId, text, attachment?.toPayload()).toDomain()
    }

    override suspend fun markRead(
        groupId: Long?,
        chatRoomId: Long,
        lastReadMessageId: Long
    ): Result<Unit> = runCatching {
        chatRemoteDataSource.markRead(groupId, chatRoomId, lastReadMessageId)
    }

    override suspend fun getReadPositions(
        groupId: Long?,
        chatRoomId: Long
    ): Result<List<ChatReadPosition>> = runCatching {
        chatRemoteDataSource.getReadPositions(groupId, chatRoomId).map { it.toDomain() }
    }

    override suspend fun sendTyping(chatRoomId: Long) {
        chatRemoteDataSource.sendTyping(chatRoomId)
    }

    override suspend fun openDirectRoom(otherUserId: Long): Result<Long> =
        runCatching {
            chatRemoteDataSource.openDirectRoom(otherUserId).id
        }

    override suspend fun getGroupDefaultChatRoom(groupId: Long): Result<Long?> =
        runCatching {
            // 서버가 생성순으로 돌려준다 — 첫 방이 그룹 생성 시 자동으로 만들어진 기본 방
            chatRemoteDataSource.getGroupDefaultChatRoom(groupId).firstOrNull()?.id
        }

    override fun observeRoomEvents(chatRoomId: Long): Flow<ChatEvent> =
        chatRemoteDataSource.subscribeRoom(chatRoomId).mapNotNull { event ->
            when (event) {
                StompSessionEvent.Connected -> ChatEvent(ChatEventType.CONNECTED, chatRoomId)
                StompSessionEvent.Disconnected -> ChatEvent(ChatEventType.DISCONNECTED, chatRoomId)
                is StompSessionEvent.Message ->
                    runCatching { json.decodeFromString<ChatSocketEventResponse>(event.body) }
                        .getOrNull()?.toDomain()
            }
        }
}

private fun GroupChatRoomResponse.toDomain() = GroupChatRoom(
    id = id,
    groupId = groupId,
    groupName = groupName,
    name = name,
    createdAt = createdAt,
    unreadCount = unreadCount,
    lastMessageText = lastMessageText,
    lastMessageType = lastMessageType,
    lastMessageAt = lastMessageAt
)

private fun DirectRoomResponse.toDomain() = DirectRoom(
    id = id,
    otherUserId = otherUserId,
    otherUserName = otherUserName,
    otherUserProfileImg = otherUserProfileImg,
    createdAt = createdAt,
    unreadCount = unreadCount,
    lastMessageText = lastMessageText,
    lastMessageType = lastMessageType,
    lastMessageAt = lastMessageAt
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
