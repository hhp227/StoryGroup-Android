package kr.hhp227.storygroup.shared.data.network.dto

import kotlinx.serialization.Serializable

// StoryGroup-WebApp /api/notifications 계약과 1:1 (notification/dto/NotificationDtos.kt NotificationResponse)
@Serializable
data class NotificationResponse(
    val id: Long,
    val type: String,
    val targetType: String? = null,
    val targetId: Long? = null,
    val isRead: Boolean,
    val createdAt: String
)

// GET /api/notifications/unread-count 응답 — notification/dto/NotificationDtos.kt UnreadCountResponse와 1:1
@Serializable
data class UnreadCountResponse(
    val count: Long
)

/**
 * STOMP /user/queue/notifications 수신 봉투 — 서버 NotificationSocketEvent/ChatBadgeSocketEvent 공용.
 * 같은 큐에 NOTIFICATION·CHAT_MESSAGE·CALL_INVITE가 섞여 오므로 type은 String으로 받고
 * 도메인 매핑에서 걸러낸다(채팅 ChatSocketEventResponse 정책 미러).
 */
@Serializable
data class PersonalSocketEventResponse(
    val type: String,
    val notification: NotificationResponse? = null,
    val chatRoomId: Long? = null,
    val messageId: Long? = null,
    val senderId: Long? = null
)
