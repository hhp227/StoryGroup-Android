package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.Post
import kr.hhp227.storygroup.shared.domain.repository.PostRepository

/** 그룹 피드 페이지 조회 — 실패는 예외로 던진다(LoginUseCase와 동일한 소비 규약) */
class GetGroupPostsUseCase(private val postRepository: PostRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(groupId: Long, page: Int, size: Int): List<Post> =
        postRepository.getPosts(groupId, page, size).getOrThrow()
}
