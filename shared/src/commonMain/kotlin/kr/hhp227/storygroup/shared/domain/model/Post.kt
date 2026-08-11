package kr.hhp227.storygroup.shared.domain.model

/** 게시글(GET /api/groups/{groupId}/posts) 도메인 모델 — 홈(라운지)/그룹 피드가 공유한다 */
data class Post(
    val id: Long,
    val groupId: Long,
    val userId: Long,
    val authorName: String,
    val authorProfileImg: String? = null,
    val text: String,
    // 첨부는 URL만 노출 — 첨부 행 id가 필요한 화면(앨범 등)은 별도 모델을 쓴다
    val imageUrls: List<String> = emptyList(),
    val videoUrls: List<String> = emptyList(),
    val isNotice: Boolean = false,
    // 서버 ISO-8601(OffsetDateTime) 원문 — 표시 포맷팅은 각 플랫폼 UI가 담당
    val createdAt: String,
    // 카드 하단 액션 바용 집계 — 카운트 미배포 서버에선 0/false로 내려앉는다
    val likeCount: Int = 0,
    val replyCount: Int = 0,
    val likedByMe: Boolean = false
)
