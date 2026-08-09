package kr.hhp227.storygroup.shared.domain.model

/** 내 정보(GET /api/users/me) 도메인 모델 — 프로필 화면/드로어 헤더가 공유한다 */
data class Profile(
    val id: Long,
    val name: String,
    val email: String,
    val profileImg: String? = null,
    val bio: String? = null,
    val statusMessage: String? = null,
    // 운영자 메뉴 노출용 — 실제 인가는 서버가 다시 검사한다
    val isAdmin: Boolean = false
)

/**
 * 내가 차단한 사용자(GET /api/users/me/blocks) 도메인 모델.
 * 차단해도 서버는 그룹 멤버 목록에서 그 사람을 빼지 않는다(웹은 "차단 해제" 버튼을 달아 유지) —
 * 앱은 이 목록으로 멤버 스트립에서 걸러낸다(차단=내 화면에서 숨김 규칙).
 */
data class BlockedUser(
    val userId: Long,
    val name: String,
    val profileImg: String? = null,
    // 서버 ISO-8601(OffsetDateTime) 원문 — 표시 포맷팅은 각 플랫폼 UI가 담당
    val blockedAt: String = ""
)

