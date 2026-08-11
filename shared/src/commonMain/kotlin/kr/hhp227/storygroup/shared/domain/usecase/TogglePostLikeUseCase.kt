package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.repository.PostRepository

/**
 * 목록 카드용 좋아요 토글 — 상세용 SetPostLikedUseCase와 달리 좋아요 목록을 다시 읽지 않는다.
 * 성공 시 리포지토리가 단건을 재조회해 postUpdates로 알리므로 목록은 그 알림으로 갱신된다.
 */
class TogglePostLikeUseCase(private val postRepository: PostRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(groupId: Long, postId: Long, liked: Boolean) {
        postRepository.setPostLiked(groupId, postId, liked).getOrThrow()
    }
}
