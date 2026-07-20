package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.Post
import kr.hhp227.storygroup.shared.domain.repository.PostRepository

/** 그룹에 게시글 작성 — 실패는 예외로 던진다(LoginUseCase와 동일한 소비 규약) */
class CreatePostUseCase(private val postRepository: PostRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(groupId: Long, text: String, images: List<String> = emptyList()): Post =
        postRepository.createPost(groupId, text, images).getOrThrow()
}
