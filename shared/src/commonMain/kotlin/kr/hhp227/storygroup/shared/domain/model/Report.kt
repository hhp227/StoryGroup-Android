package kr.hhp227.storygroup.shared.domain.model

/**
 * 신고 처리 상태 — 사용자/게시글 신고 공통(웹 lib/api ReportStatus 미러).
 * 같은 대상의 "대기중" 신고는 1건만(중복 409), 처리(확인/기각)된 뒤에는 재신고할 수 있다.
 */
enum class ReportStatus { PENDING, RESOLVED, DISMISSED }

/**
 * 그룹 신고함(모더레이터 전용) 한 행 — GET /api/groups/{id}/reports 도메인 모델.
 * 신고된 게시글 요약이 함께 내려온다. 처리(확인/기각)는 기록일 뿐이고
 * 실제 조치(게시글 삭제 등)는 게시글 화면의 기존 기능으로 한다(웹 미러).
 */
data class PostReport(
    val id: Long,
    val postId: Long,
    // 본문 없이 첨부만 있는 게시글이면 빈 문자열 — 화면이 대체 문구를 그린다
    val postTextPreview: String,
    val postAuthorId: Long,
    val postAuthorName: String,
    val reporterId: Long,
    val reporterName: String,
    val reason: String? = null,
    val status: ReportStatus = ReportStatus.PENDING,
    // 서버 ISO-8601 원문 — 표시 포맷팅은 각 플랫폼 UI가 담당
    val createdAt: String = "",
    val processedAt: String? = null
)
