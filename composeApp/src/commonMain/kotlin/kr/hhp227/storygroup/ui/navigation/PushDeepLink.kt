package kr.hhp227.storygroup.ui.navigation

/** 푸시 payload data → 화면 라우팅 정보(설계 §9). 파싱 실패는 null 필드로 강등 — 알림 탭 폴백 */
data class PushDeepLink(
    val kind: String,
    val chatRoomId: Long? = null,
    val groupId: Long? = null,
    val postId: Long? = null,
    val roomTitle: String? = null
) {
    companion object {
        fun fromData(data: Map<String, String>): PushDeepLink = PushDeepLink(
            kind = data["kind"] ?: "NOTIFICATION",
            chatRoomId = data["chatRoomId"]?.toLongOrNull(),
            groupId = data["groupId"]?.toLongOrNull(),
            postId = data["postId"]?.toLongOrNull(),
            roomTitle = data["roomTitle"]
        )
    }
}
