package kr.hhp227.storygroup.shared.domain.repository

import kr.hhp227.storygroup.shared.domain.model.Post

interface PostRepository {
    /** 그룹 피드 페이지 조회(최신순) — GET /api/groups/{groupId}/posts?page=&size= */
    suspend fun getPosts(groupId: Long, page: Int, size: Int): Result<List<Post>>
}
