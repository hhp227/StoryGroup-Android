package kr.hhp227.storygroup.shared.data.network.dto

import kotlinx.serialization.Serializable

// StoryGroup-WebApp /api/groups/{groupId}/posts 계약과 1:1 (post/dto/PostDtos.kt)

// text에 공백 검증이 없는 것은 첨부만 있는 게시글을 허용하는 백엔드 규칙 미러 —
// 앱 MVP는 텍스트만 보내므로 "본문 필수" 검증은 각 플랫폼 ViewModel이 한다.
@Serializable
data class CreatePostRequest(
    val text: String,
    val images: List<String>? = null,
    val videos: List<String>? = null
)

// 게시글 신고 — 접수는 그 그룹의 모더레이터 신고함으로 간다(웹 reportPost 미러).
// reason은 선택(웹도 사유 입력 UI 없이 null을 보낸다), 같은 글의 "대기중" 신고는 1건만(중복 409).
@Serializable
data class ReportPostRequest(
    val reason: String? = null
)

// images/videos는 3상태 계약이다 — null이면 기존 첨부 유지, 빈 리스트면 전부 삭제, 값이 있으면 전체 교체.
// 앱은 폼이 들고 있는 목록을 항상 통째로 보내(전체 교체) "안 건드림"과 "다 지움"을 구분할 일이 없게 한다.
// videos는 앱 작성 폼에 동영상 첨부가 없으므로 null로 두어 기존 첨부를 건드리지 않는다.
@Serializable
data class UpdatePostRequest(
    val text: String,
    val images: List<String>? = null,
    val videos: List<String>? = null
)

@Serializable
data class PostResponse(
    val id: Long,
    val groupId: Long,
    val userId: Long,
    val authorName: String,
    val authorProfileImg: String? = null,
    val text: String,
    val images: List<ImageResponse> = emptyList(),
    // 웹과 동일한 방어 — videos를 모르는 배포 전 백엔드는 이 필드를 안 내려준다
    val videos: List<VideoResponse> = emptyList(),
    val isNotice: Boolean = false,
    val createdAt: String
)

@Serializable
data class ImageResponse(
    val id: Long,
    val image: String
)

// 게시글 상세 — 댓글/좋아요 (/posts/{postId}/comments, /posts/{postId}/likes)

@Serializable
data class CommentResponse(
    val id: Long,
    val postId: Long,
    val userId: Long,
    val authorName: String,
    val authorProfileImg: String? = null,
    val parentReplyId: Long? = null,
    val text: String,
    val createdAt: String
)

// parentReplyId가 null이면 최상위 댓글 — 답글일 때만 실어 보낸다.
@Serializable
data class CreateCommentRequest(
    val text: String,
    val parentReplyId: Long? = null
)

@Serializable
data class PostLikeResponse(
    val userId: Long,
    val authorName: String,
    val authorProfileImg: String? = null,
    val createdAt: String
)

@Serializable
data class VideoResponse(
    val id: Long,
    val video: String
)
