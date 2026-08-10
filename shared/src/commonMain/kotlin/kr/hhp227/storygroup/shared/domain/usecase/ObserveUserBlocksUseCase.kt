package kr.hhp227.storygroup.shared.domain.usecase

import kotlinx.coroutines.flow.Flow
import kr.hhp227.storygroup.shared.domain.repository.UserRepository

/**
 * 사용자 차단 알림 스트림(차단한 userId) — 목록 화면이 자기 PagingData 스냅샷에서
 * 그 작성자의 글만 걷어내는 데 쓴다. Paging3에는 항목 제거 API가 없고 refresh는 첫 페이지부터
 * 전체 재조회라, 이미 쌓아둔 페이지와 스크롤 위치를 잃기 때문이다(ObservePostUpdatesUseCase와 같은 규약).
 */
class ObserveUserBlocksUseCase(private val userRepository: UserRepository) {
    operator fun invoke(): Flow<Long> = userRepository.userBlocks
}
