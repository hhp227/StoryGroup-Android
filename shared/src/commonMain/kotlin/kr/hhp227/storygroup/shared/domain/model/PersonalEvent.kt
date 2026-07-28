package kr.hhp227.storygroup.shared.domain.model

/**
 * 개인 큐 실시간 이벤트 종류 — 서버 envelope type 미러에 클라 합성 2종을 더한 것.
 * CONNECTED/DISCONNECTED는 채팅방 ChatEventType과 같은 규칙: 소켓 세션 수립/유실 시 클라가 만들어
 * 흘려, 구독 화면이 REST 재조회(끊김 공백 메꿈)를 트리거하게 한다.
 */
enum class PersonalEventType {
    CONNECTED, DISCONNECTED,
    NOTIFICATION, CHAT_MESSAGE
}

/**
 * 개인 큐(/user/queue/notifications) 실시간 이벤트 — type별로 채워지는 필드가 다르다.
 * NOTIFICATION은 notification, CHAT_MESSAGE는 chatRoomId/messageId/senderId만 온다
 * (뱃지에는 "어느 방에 새 메시지" 사실만 필요해 서버가 본문을 싣지 않는 계약).
 */
data class PersonalEvent(
    val type: PersonalEventType,
    val notification: AppNotification? = null,
    val chatRoomId: Long? = null,
    val messageId: Long? = null,
    val senderId: Long? = null
)
