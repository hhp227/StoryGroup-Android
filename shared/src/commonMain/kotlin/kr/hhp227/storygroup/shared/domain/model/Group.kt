package kr.hhp227.storygroup.shared.domain.model

/** 그룹 내 내 역할 — OWNER(방장)/ADMIN(부방장)은 조정 권한 보유 */
enum class GroupRole { OWNER, ADMIN, MEMBER }

/** 가입 방식 — AUTO_APPROVE(바로 가입)/APPROVAL_REQUIRED(승인제) */
enum class GroupJoinType { AUTO_APPROVE, APPROVAL_REQUIRED }

/** 내 그룹(GET /api/groups) 도메인 모델 — 홈(라운지 해석)/그룹 탭이 공유한다 */
data class Group(
    val id: Long,
    val name: String,
    val description: String? = null,
    val image: String? = null,
    val joinType: GroupJoinType = GroupJoinType.AUTO_APPROVE,
    val myRole: GroupRole = GroupRole.MEMBER,
    // 서버 ISO-8601(OffsetDateTime) 원문 — 표시 포맷팅은 각 플랫폼 UI가 담당
    val createdAt: String = "",
    val isLounge: Boolean = false
)
