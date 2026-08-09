package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.Post
import kr.hhp227.storygroup.shared.domain.repository.PostRepository

/**
 * 게시글 단건 — 수정 화면이 기존 본문·첨부를 채울 때 쓴다.
 * 상세 화면의 [GetPostDetailUseCase]는 좋아요·댓글까지 함께 읽으므로 폼에는 과하다.
 */
class GetPostUseCase(private val postRepository: PostRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(groupId: Long, postId: Long): Post =
        postRepository.getPost(groupId, postId).getOrThrow()
}
