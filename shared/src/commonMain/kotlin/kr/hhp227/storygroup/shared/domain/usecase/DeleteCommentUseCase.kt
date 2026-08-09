package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.repository.PostRepository

/** 댓글 삭제 — 권한(본인 글) 판정은 서버가 하고, 실패는 예외로 올라온다 */
class DeleteCommentUseCase(private val postRepository: PostRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(groupId: Long, postId: Long, commentId: Long) {
        postRepository.deleteComment(groupId, postId, commentId).getOrThrow()
    }
}
