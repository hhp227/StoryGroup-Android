package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.Post
import kr.hhp227.storygroup.shared.domain.repository.PostRepository

/** 라운지(홈 피드)에 게시글 작성 — 라운지 해석 포함, 실패는 예외로 던진다 */
class CreateLoungePostUseCase(private val postRepository: PostRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(text: String): Post =
        postRepository.createLoungePost(text).getOrThrow()
}
