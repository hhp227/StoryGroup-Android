package kr.hhp227.storygroup.shared.domain.model

/**
 * 게시글 댓글 — 웹 상세 페이지 계약 미러(/api/groups/{groupId}/posts/{postId}/comments).
 * 서버는 댓글과 답글을 한 목록으로 내려주고 parentReplyId로만 구분한다(답글의 답글은 없다) —
 * 화면이 parentReplyId == null인 것을 최상위로 두고 나머지를 그 아래로 묶는다.
 */
data class Comment(
    val id: Long,
    val postId: Long,
    val userId: Long,
    val authorName: String,
    val authorProfileImg: String? = null,
    val parentReplyId: Long? = null,
    val text: String,
    val createdAt: String
)

/** 게시글 좋아요 한 건 — 누른 사람이 누구인지까지 내려온다(내가 눌렀는지 판정에 쓴다) */
data class PostLike(
    val userId: Long,
    val authorName: String,
    val authorProfileImg: String? = null,
    val createdAt: String
)
