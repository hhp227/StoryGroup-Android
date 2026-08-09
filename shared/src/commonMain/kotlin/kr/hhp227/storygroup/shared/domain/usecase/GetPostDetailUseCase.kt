package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.Comment
import kr.hhp227.storygroup.shared.domain.model.Post
import kr.hhp227.storygroup.shared.domain.model.PostLike
import kr.hhp227.storygroup.shared.domain.repository.PostRepository

/** 상세 화면이 한 번에 필요로 하는 세 가지 — 본문·좋아요·댓글 */
data class PostDetail(
    val post: Post,
    val likes: List<PostLike>,
    val comments: List<Comment>
)

/**
 * 게시글 상세 로드 — 본문·좋아요·댓글을 한 번에 읽는다.
 * 세 요청을 화면에서 각각 부르면 부분 실패마다 화면 상태가 갈라지므로 여기서 묶는다
 * (하나라도 실패하면 통째로 실패 — 반쪽짜리 상세를 보여주지 않는다).
 */
class GetPostDetailUseCase(private val postRepository: PostRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(groupId: Long, postId: Long): PostDetail = PostDetail(
        post = postRepository.getPost(groupId, postId).getOrThrow(),
        likes = postRepository.getPostLikes(groupId, postId).getOrThrow(),
        comments = postRepository.getComments(groupId, postId).getOrThrow()
    )
}
