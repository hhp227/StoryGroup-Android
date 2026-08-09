package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.PostLike
import kr.hhp227.storygroup.shared.domain.repository.PostRepository

/**
 * 좋아요 설정/해제 후 갱신된 목록을 돌려준다 — 서버 응답이 void라 개수를 화면에서 추측하지 않고
 * 다시 읽는다(다른 사람이 그 사이 누른 것까지 반영된다).
 */
class SetPostLikedUseCase(private val postRepository: PostRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(groupId: Long, postId: Long, liked: Boolean): List<PostLike> {
        postRepository.setPostLiked(groupId, postId, liked).getOrThrow()
        return postRepository.getPostLikes(groupId, postId).getOrThrow()
    }
}
