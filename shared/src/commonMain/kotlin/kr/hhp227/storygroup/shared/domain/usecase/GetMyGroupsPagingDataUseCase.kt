package kr.hhp227.storygroup.shared.domain.usecase

import app.cash.paging.PagingData
import kotlinx.coroutines.flow.Flow
import kr.hhp227.storygroup.shared.domain.model.Group
import kr.hhp227.storygroup.shared.domain.repository.GroupRepository

/**
 * 내 그룹 목록 Paging 스트림(라운지 제외) — cachedIn 없이 반환하고 각 플랫폼 프레젠테이션
 * 경계에서 캐시한다(Android=viewModelScope, iOS=구독 스코프 — docs/KMP.md 규약)
 */
class GetMyGroupsPagingDataUseCase(private val groupRepository: GroupRepository) {
    operator fun invoke(): Flow<PagingData<Group>> = groupRepository.getMyGroupsPagingData()
}
