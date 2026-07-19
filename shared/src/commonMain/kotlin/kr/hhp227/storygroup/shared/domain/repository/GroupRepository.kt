package kr.hhp227.storygroup.shared.domain.repository

import app.cash.paging.PagingData
import kotlinx.coroutines.flow.Flow
import kr.hhp227.storygroup.shared.domain.model.Group
import kr.hhp227.storygroup.shared.domain.model.GroupMember

interface GroupRepository {
    /**
     * 내 그룹 Paging 스트림(라운지 제외 — 탭 목록 규칙) — 레거시 user_groups?offset&load_size 미러.
     * cachedIn은 각 플랫폼 프레젠테이션 경계에서 적용한다.
     */
    fun getMyGroupsPagingData(): Flow<PagingData<Group>>

    /** 내가 가입한 그룹 목록(라운지 포함) — GET /api/groups */
    suspend fun getMyGroups(): Result<List<Group>>

    /** 그룹 단건 조회(상세 진입 시 신선화) — GET /api/groups/{id} */
    suspend fun getGroup(groupId: Long): Result<Group>

    /** 그룹 멤버 목록 — GET /api/groups/{id}/members */
    suspend fun getMembers(groupId: Long): Result<List<GroupMember>>
}
