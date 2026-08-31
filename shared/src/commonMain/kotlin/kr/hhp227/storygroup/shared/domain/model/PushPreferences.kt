package kr.hhp227.storygroup.shared.domain.model

/**
 * 푸시 종류별 on/off(계정 단위) — 채팅(그룹채팅·DM) / 활동 알림(알림 피드 전부).
 * 인앱 실시간·뱃지·알림 목록과 무관하게 OS 푸시 발송만 서버가 걸러낸다. 모든 기기 공통.
 */
data class PushPreferences(
    val chatEnabled: Boolean,
    val activityEnabled: Boolean
)
