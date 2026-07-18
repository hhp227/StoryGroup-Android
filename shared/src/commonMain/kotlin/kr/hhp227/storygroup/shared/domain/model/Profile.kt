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
