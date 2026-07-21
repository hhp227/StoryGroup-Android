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

/** 그룹 탐색 화면에서 본 그룹과 나의 관계 — MEMBER는 역할과 무관하게 "이미 가입됨"을 뜻한다 */
enum class GroupMembershipStatus { NONE, PENDING, MEMBER }

/** 그룹 탐색(GET /api/groups/discover) 정렬 기준 */
enum class DiscoverSort { RECENT, POPULAR }

/** 그룹 탐색 결과 도메인 모델(GET /api/groups/discover) — 미가입 그룹도 포함(라운지 제외) */
data class DiscoverGroup(
    val id: Long,
    val name: String,
    val description: String? = null,
    val image: String? = null,
    val joinType: GroupJoinType = GroupJoinType.AUTO_APPROVE,
    val memberCount: Long = 0,
    val membership: GroupMembershipStatus = GroupMembershipStatus.NONE,
    val createdAt: String = ""
)

/** 그룹 가입 결과 — 자동 승인이면 JOINED+가입된 그룹, 승인제면 REQUESTED+group=null(POST .../join) */
enum class JoinResult { JOINED, REQUESTED }

data class JoinGroupResult(val status: JoinResult, val group: Group?)
