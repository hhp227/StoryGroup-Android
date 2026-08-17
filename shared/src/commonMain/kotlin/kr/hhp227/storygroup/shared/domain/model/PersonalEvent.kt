package kr.hhp227.storygroup.shared.domain.model

/**
 * 개인 큐 실시간 이벤트 종류 — 서버 envelope type 미러에 클라 합성 2종을 더한 것.
 * CONNECTED/DISCONNECTED는 채팅방 ChatEventType과 같은 규칙: 소켓 세션 수립/유실 시 클라가 만들어
 * 흘려, 구독 화면이 REST 재조회(끊김 공백 메꿈)를 트리거하게 한다.
 */
enum class PersonalEventType {
    CONNECTED, DISCONNECTED,
    NOTIFICATION, CHAT_MESSAGE, CALL_INVITE, PRESENCE_CHANGED
}

/**
 * 개인 큐(/user/queue/notifications) 실시간 이벤트 — type별로 채워지는 필드가 다르다.
 * NOTIFICATION은 notification, CHAT_MESSAGE는 chatRoomId/messageId/senderId만 온다
 * (뱃지에는 "어느 방에 새 메시지" 사실만 필요해 서버가 본문을 싣지 않는 계약).
 * CALL_INVITE는 chatRoomId/senderId/senderName — DB에 남지 않는 휘발 벨울림이라
 * 그 순간에만 의미가 있다(부재중 이력 없음, 웹 D6 미러).
 */
data class PersonalEvent(
    val type: PersonalEventType,
    val notification: AppNotification? = null,
    val chatRoomId: Long? = null,
    val messageId: Long? = null,
    val senderId: Long? = null,
    val senderName: String? = null,
    // CALL_INVITE 그룹 방 벨울림 전용(페이스톡 미러) — 배너 제목/이동 경로용, DM이면 null
    val groupId: Long? = null,
    val roomName: String? = null,
    // CALL_INVITE 전용 — false면 보이스톡(수신 측이 배너 표시·카메라 OFF 입장을 결정한다)
    val video: Boolean = true,
    // PRESENCE_CHANGED 전용 — 전환한 친구의 id. 스냅샷 복구는 친구 목록 응답 online이 담당
    val userId: Long? = null,
    // PRESENCE_CHANGED 전용 — true=온라인 전환, false=오프라인 전환(서버 10초 유예 후 확정)
    val online: Boolean = false
)
