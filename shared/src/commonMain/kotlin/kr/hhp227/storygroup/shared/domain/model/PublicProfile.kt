package kr.hhp227.storygroup.shared.domain.model

/**
 * 공개 프로필 — GET /api/users/{userId}. 내 프로필(Profile, email·isAdmin 포함)과 화면 계약이 달라 별개 타입.
 * createdAt은 서버 ISO 문자열 그대로 — 표시 포맷팅(가입일)은 플랫폼 UI 몫(Post 관용구)
 */
data class PublicProfile(
    val id: Long,
    val name: String,
    val profileImg: String? = null,
    val bio: String? = null,
    val statusMessage: String? = null,
    val createdAt: String = ""
)
