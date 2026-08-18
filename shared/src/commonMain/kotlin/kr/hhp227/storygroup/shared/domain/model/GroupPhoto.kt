package kr.hhp227.storygroup.shared.domain.model

/**
 * 그룹 앨범 항목 — 별도 엔티티가 아니라 그룹 게시글 첨부의 파생 뷰(웹 lib/api.ts GroupPhoto 미러).
 * 동영상은 image에 동영상 URL이 그대로 온다(서버 썸네일 없음 — 클라이언트가 첫 프레임을 뽑는다).
 * postId로 원본 게시글로 이동한다(맥락 보존).
 */
data class GroupPhoto(
    val id: Long,
    val postId: Long,
    val image: String,
    val mediaType: GroupPhotoMediaType,
    val userId: Long,
    val authorName: String,
    val createdAt: String
)

enum class GroupPhotoMediaType { IMAGE, VIDEO }
