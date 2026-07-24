package kr.hhp227.storygroup.shared.domain.model

/** 그룹 멤버(GET /api/groups/{id}/members) 도메인 모델 */
data class GroupMember(
    val userId: Long,
    val name: String,
    val profileImg: String? = null,
    val role: GroupRole = GroupRole.MEMBER,
    // 서버 ISO-8601(OffsetDateTime) 원문 — 표시 포맷팅은 각 플랫폼 UI가 담당
    val joinedAt: String = ""
)

/** 승인 대기 중인 가입 신청(GET /api/groups/{id}/join-requests) 도메인 모델 — 모더레이터 전용 조회 */
data class GroupJoinRequest(
    val userId: Long,
    val name: String,
    val profileImg: String? = null,
    // 서버 ISO-8601(OffsetDateTime) 원문 — 표시 포맷팅은 각 플랫폼 UI가 담당
    val requestedAt: String = ""
)
