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
    val createdAt: String,
    // 서버가 target에서 역추적한 컨텍스트 — 대상 삭제/접근 불가·구서버는 null
    val postId: Long? = null,
    val postPreview: String? = null,
    val groupId: Long? = null,
    val groupName: String? = null
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
    val senderId: Long? = null,
    // CHAT_MESSAGE 전용 — 허브 목록 미리보기 갱신용 본문(구서버는 필드가 없어 null)
    val text: String? = null,
    val attachmentType: String? = null,
    val createdAt: String? = null,
    // CALL_INVITE 전용 — 서버 CallInviteEvent는 발신자를 fromUserId/fromUserName으로 싣는다
    val fromUserId: Long? = null,
    val fromUserName: String? = null,
    // CALL_INVITE 그룹 방 벨울림 전용(페이스톡 전환) — DM이면 null
    val groupId: Long? = null,
    val roomName: String? = null,
    // CALL_INVITE 전용 — false면 보이스톡. 구서버는 필드가 없어 null(페이스톡 간주)
    val video: Boolean? = null,
    // PRESENCE_CHANGED 전용
    val userId: Long? = null,
    val online: Boolean? = null
)
