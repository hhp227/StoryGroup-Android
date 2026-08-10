package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.Post
import kr.hhp227.storygroup.shared.domain.repository.PostRepository

/** 게시글 수정 — 작성자 본인만(서버가 판정). images/videos는 폼이 들고 있는 목록으로 전체 교체된다 */
class UpdatePostUseCase(private val postRepository: PostRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(
        groupId: Long,
        postId: Long,
        text: String,
        images: List<String> = emptyList(),
        videos: List<String> = emptyList()
    ): Post = postRepository.updatePost(groupId, postId, text, images, videos).getOrThrow()
}
