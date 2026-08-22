package kr.hhp227.storygroup.shared.domain.model

/** 알림 종류 — 서버 enum과 1:1(wire 문자열). MENTION/CHAT/INVITE는 서버에 예약만 돼 있고 아직 생성되지 않는다 */
enum class NotificationType {
    NEW_POST, COMMENT, LIKE, MENTION, CHAT, MEETING_STARTED, NOTICE, INVITE,
    JOIN_REQUEST, JOIN_APPROVED, JOIN_REJECTED
}

/** 알림 대상 참조 종류 — 타입별 이동 라우팅용 예약(웹도 아직 미구현이라 앱도 표시만 한다) */
enum class NotificationTargetType { POST, REPLY, MESSAGE, MEETING, GROUP }

/**
 * 알림(GET /api/notifications) 도메인 모델 — 웹 AppNotification 미러(Foundation.Notification과의
 * Swift 이름 충돌을 웹과 같은 방식으로 회피). 행위자는 서버가 저장하지 않아 여전히 없다.
 */
data class AppNotification(
    val id: Long,
    val type: NotificationType,
    val targetType: NotificationTargetType? = null,
    val targetId: Long? = null,
    val isRead: Boolean = false,
    // 서버 ISO-8601(OffsetDateTime) 원문 — 표시 포맷팅은 각 플랫폼 UI가 담당
    val createdAt: String = "",
    // 서버가 target에서 역추적한 컨텍스트(어떤 게시글/그룹의 알림인지) — 대상 삭제/접근 불가면 null
    val postId: Long? = null,
    val postPreview: String? = null,
    val groupId: Long? = null,
    val groupName: String? = null
)
