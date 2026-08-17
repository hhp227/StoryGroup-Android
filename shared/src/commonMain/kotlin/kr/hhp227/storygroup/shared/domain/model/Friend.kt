package kr.hhp227.storygroup.shared.domain.model

/**
 * 친구 — 단방향 등록(즐겨찾기 성격, 승인 절차 없음, 차단과 같은 구조).
 * userId는 상대방 id(내 id가 아니다).
 */
data class Friend(
    val userId: Long,
    val name: String,
    val profileImg: String? = null,
    val statusMessage: String? = null,
    val friendedAt: String = "",
    // 전역 프레즌스 스냅샷 — 구서버(필드 없음)는 false. 실시간 전환은 PersonalEvent PRESENCE_CHANGED
    val online: Boolean = false
)

/** 통합검색 users 섹션 항목 — 같은 그룹 소속 사용자만 노출된다(서버 필터, 본인·차단 제외) */
data class UserSearchResult(
    val id: Long,
    val name: String,
    val profileImg: String? = null,
    val statusMessage: String? = null
)
