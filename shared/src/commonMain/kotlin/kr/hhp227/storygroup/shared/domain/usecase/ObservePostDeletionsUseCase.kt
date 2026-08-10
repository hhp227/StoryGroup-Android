package kr.hhp227.storygroup.shared.domain.usecase

import kotlinx.coroutines.flow.Flow
import kr.hhp227.storygroup.shared.domain.repository.PostRepository

/**
 * 게시글 삭제 알림 스트림(삭제된 postId) — 목록 화면이 자기 PagingData 스냅샷에서 그 글만
 * 걷어내는 데 쓴다. Paging3에는 항목 제거 API가 없고 refresh는 첫 페이지부터 전체 재조회라
 * 이미 쌓아둔 페이지와 스크롤 위치를 잃는다(ObservePostUpdatesUseCase와 같은 규약).
 */
class ObservePostDeletionsUseCase(private val postRepository: PostRepository) {
    operator fun invoke(): Flow<Long> = postRepository.postDeletions
}
