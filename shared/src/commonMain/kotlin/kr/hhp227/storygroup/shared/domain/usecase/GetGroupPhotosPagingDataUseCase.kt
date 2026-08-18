package kr.hhp227.storygroup.shared.domain.usecase

import app.cash.paging.PagingData
import kotlinx.coroutines.flow.Flow
import kr.hhp227.storygroup.shared.domain.model.GroupPhoto
import kr.hhp227.storygroup.shared.domain.repository.GroupRepository

/** 그룹 앨범 Paging 스트림 — cachedIn 없이 반환하고 각 플랫폼 프레젠테이션 경계에서 캐시한다 */
class GetGroupPhotosPagingDataUseCase(private val groupRepository: GroupRepository) {
    operator fun invoke(groupId: Long): Flow<PagingData<GroupPhoto>> =
        groupRepository.getGroupPhotosPagingData(groupId)
}
