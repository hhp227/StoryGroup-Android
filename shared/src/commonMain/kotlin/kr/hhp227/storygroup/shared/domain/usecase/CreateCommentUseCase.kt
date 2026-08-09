package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.Comment
import kr.hhp227.storygroup.shared.domain.repository.PostRepository

/** 댓글·답글 작성 — parentReplyId가 있으면 그 댓글의 답글이 된다 */
class CreateCommentUseCase(private val postRepository: PostRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(groupId: Long, postId: Long, text: String, parentReplyId: Long? = null): Comment =
        postRepository.createComment(groupId, postId, text, parentReplyId).getOrThrow()
}
