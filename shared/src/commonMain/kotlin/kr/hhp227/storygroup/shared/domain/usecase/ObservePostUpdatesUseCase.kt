package kr.hhp227.storygroup.shared.domain.usecase

import kotlinx.coroutines.flow.Flow
import kr.hhp227.storygroup.shared.domain.model.Post
import kr.hhp227.storygroup.shared.domain.repository.PostRepository

/**
 * 게시글 수정 알림 스트림 — 목록 화면이 자기 PagingData 스냅샷에서 그 항목만 갈아끼우는 데 쓴다.
 * Paging3에는 항목 단위 갱신 API가 없고 refresh는 첫 페이지부터 전체 재조회라, 이미 쌓아둔
 * 페이지와 스크롤 위치를 잃기 때문이다(수정은 목록 구조를 바꾸지 않으므로 재조회가 필요 없다).
 * 작성·삭제는 목록 구조가 바뀌므로 기존대로 refresh를 태운다.
 */
class ObservePostUpdatesUseCase(private val postRepository: PostRepository) {
    operator fun invoke(): Flow<Post> = postRepository.postUpdates
}
