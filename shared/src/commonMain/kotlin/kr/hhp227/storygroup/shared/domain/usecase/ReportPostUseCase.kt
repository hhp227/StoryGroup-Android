package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.repository.PostRepository

/**
 * 게시글 신고 — 접수는 그 그룹 모더레이터의 신고함으로 간다(웹 게시글 상세의 "신고" 미러).
 * 사유는 선택이고, 같은 글의 대기중 신고가 이미 있으면 서버가 409로 막는다(메시지를 그대로 노출).
 */
class ReportPostUseCase(private val postRepository: PostRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(groupId: Long, postId: Long, reason: String? = null) {
        postRepository.reportPost(groupId, postId, reason).getOrThrow()
    }
}
