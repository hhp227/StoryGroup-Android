package kr.hhp227.storygroup.shared.data.network.dto

import kotlinx.serialization.Serializable

// StoryGroup-WebApp chat/dto/ChatDtos.kt 계약과 1:1

/** GET /api/chat-rooms — 내 그룹 채팅방(라운지는 서버가 제외) */
@Serializable
data class GroupChatRoomResponse(
    val id: Long,
    val groupId: Long,
    val groupName: String,
    val name: String,
    val createdAt: String
)

/** GET /api/dm — 내 DM 방(방 이름 대신 상대 정보로 표시) */
@Serializable
data class DirectRoomResponse(
    val id: Long,
    val otherUserId: Long,
    val otherUserName: String,
    val otherUserProfileImg: String? = null,
    val createdAt: String
)

@Serializable
data class MessageAttachmentResponse(
    val url: String,
    val name: String? = null,
    val contentType: String? = null,
    val size: Long? = null
)

/** 메시지(이력 조회·전송 응답·STOMP 브로드캐스트 공용) — 첨부 전용 메시지는 text가 빈 문자열 */
@Serializable
data class MessageResponse(
    val id: Long,
    val chatRoomId: Long,
    val userId: Long,
    val authorName: String,
    val authorProfileImg: String? = null,
    val text: String,
    val attachment: MessageAttachmentResponse? = null,
    val createdAt: String
)

/** POST .../messages 요청의 attachment — /api/files 업로드 응답을 그대로 실어 보낸다 */
@Serializable
data class MessageAttachmentPayload(
    val url: String,
    val name: String? = null,
    val contentType: String? = null,
    val size: Long? = null
)

/** POST .../messages — text와 attachment 둘 다 비면 서버가 400. 첨부는 메시지당 1개 */
@Serializable
data class CreateMessageRequest(
    val text: String,
    val attachment: MessageAttachmentPayload? = null
)

/** POST /api/dm/{otherUserId} 응답 — get-or-create라 200 고정, name은 서버가 "DM" 고정 문자열 */
@Serializable
data class ChatRoomResponse(
    val id: Long,
    val groupId: Long? = null,
    val name: String,
    val createdAt: String
)

/** GET {room}/reads — 방 멤버별 마지막 읽음 위치(본인 포함, 읽음 기록 있는 멤버만) */
@Serializable
data class ReadPositionResponse(
    val userId: Long,
    val lastReadMessageId: Long
)

/** PUT .../read — 서버가 GREATEST로 단조 증가를 보장하므로 낡은 값 전송도 안전 */
@Serializable
data class MarkChatReadRequest(
    val lastReadMessageId: Long
)

/**
 * STOMP /topic/chat-rooms/{id} 수신 봉투 — 서버 realtime/ChatSocketEvent.kt 1:1.
 * type은 미지의 값 추가에 대비해 String으로 받고 도메인 매핑에서 걸러낸다(알림 정책 미러).
 */
@Serializable
data class ChatSocketEventResponse(
    val type: String,
    val chatRoomId: Long,
    val message: MessageResponse? = null,
    val messageId: Long? = null,
    val userId: Long? = null,
    val userName: String? = null,
    val users: List<PresenceUserResponse>? = null
)

@Serializable
data class PresenceUserResponse(
    val userId: Long,
    val userName: String
)
