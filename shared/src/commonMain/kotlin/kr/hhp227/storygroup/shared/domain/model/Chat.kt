package kr.hhp227.storygroup.shared.domain.model

/** 그룹 채팅방(GET /api/chat-rooms) — 웹 GroupChatRoom 미러, 라운지는 서버가 제외 */
data class GroupChatRoom(
    val id: Long,
    val groupId: Long,
    val groupName: String,
    val name: String,
    // 서버 ISO-8601(OffsetDateTime) 원문 — 표시 포맷팅은 각 플랫폼 UI가 담당
    val createdAt: String = "",
    // 내 읽음 위치 이후의 남의 메시지 수(차단·삭제 제외) — 서버 집계 스냅숏, 실시간 증가는 개인 큐 이벤트로 클라가 얹는다
    val unreadCount: Long = 0
)

/** 1:1 DM 방(GET /api/dm) — 웹 DirectRoom 미러. 방 이름은 서버에 "DM" 고정이라 상대 정보로 표시한다 */
data class DirectRoom(
    val id: Long,
    val otherUserId: Long,
    val otherUserName: String,
    val otherUserProfileImg: String? = null,
    val createdAt: String = "",
    val unreadCount: Long = 0
)

/** 메시지 첨부(메시지당 최대 1개) — 이미지/파일 구분은 contentType으로 판단 */
data class ChatAttachment(
    val url: String,
    val name: String? = null,
    val contentType: String? = null,
    val size: Long? = null
) {
    val isImage: Boolean get() = contentType?.startsWith("image/") == true
}

/** 채팅 메시지 — 첨부 전용 메시지는 text가 빈 문자열 */
data class ChatMessage(
    val id: Long,
    val chatRoomId: Long,
    val userId: Long,
    val authorName: String,
    val authorProfileImg: String? = null,
    val text: String = "",
    val attachment: ChatAttachment? = null,
    val createdAt: String = ""
)

/** 방 멤버별 마지막 읽음 위치 — "읽음 N"은 이 위치들로 클라가 파생한다(서버에 읽음 수 개념 없음) */
data class ChatReadPosition(
    val userId: Long,
    val lastReadMessageId: Long
)

/**
 * 채팅방 실시간 이벤트 종류 — 서버 ChatSocketEventType 미러에 클라 합성 2종을 더한 것.
 * CONNECTED/DISCONNECTED는 서버가 보내지 않는다: 소켓 세션 수립/유실 시 클라가 만들어 흘려
 * 화면이 이력 재조회(끊김 공백 메꿈)를 트리거하게 한다.
 */
enum class ChatEventType {
    CONNECTED, DISCONNECTED,
    MESSAGE_CREATED, MESSAGE_UPDATED, MESSAGE_DELETED, TYPING, PRESENCE, READ
}

/** 채팅방 실시간 이벤트 — type별로 채워지는 필드가 다르다(서버 봉투 미러) */
data class ChatEvent(
    val type: ChatEventType,
    val chatRoomId: Long,
    val message: ChatMessage? = null,
    val messageId: Long? = null,
    val userId: Long? = null,
    val userName: String? = null
)
