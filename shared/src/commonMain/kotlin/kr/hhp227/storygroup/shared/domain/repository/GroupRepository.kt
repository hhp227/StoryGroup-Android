package kr.hhp227.storygroup.shared.domain.repository

import app.cash.paging.PagingData
import kotlinx.coroutines.flow.Flow
import kr.hhp227.storygroup.shared.domain.model.DiscoverGroup
import kr.hhp227.storygroup.shared.domain.model.DiscoverSort
import kr.hhp227.storygroup.shared.domain.model.Group
import kr.hhp227.storygroup.shared.domain.model.GroupJoinRequest
import kr.hhp227.storygroup.shared.domain.model.GroupJoinType
import kr.hhp227.storygroup.shared.domain.model.GroupMember
import kr.hhp227.storygroup.shared.domain.model.JoinGroupResult

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

    /** 그룹 생성 — POST /api/groups */
    suspend fun createGroup(name: String, description: String?, image: String?, joinType: GroupJoinType): Result<Group>

    /**
     * 그룹 탐색 Paging 스트림(라운지 제외) — GET /api/groups/discover.
     * cachedIn은 각 플랫폼 프레젠테이션 경계에서 적용한다.
     */
    fun getDiscoverGroupsPagingData(query: String, sort: DiscoverSort): Flow<PagingData<DiscoverGroup>>

    /** 그룹 가입/신청 — POST /api/groups/{id}/join */
    suspend fun joinGroup(groupId: Long): Result<JoinGroupResult>

    /** 가입 신청 취소 — DELETE /api/groups/{id}/join */
    suspend fun cancelJoinRequest(groupId: Long): Result<Unit>

    /** 승인 대기 가입 신청 목록(모더레이터 전용) — GET /api/groups/{id}/join-requests */
    suspend fun getJoinRequests(groupId: Long): Result<List<GroupJoinRequest>>

    /** 가입 신청 승인(모더레이터 전용) — POST /api/groups/{id}/join-requests/{userId}/approve */
    suspend fun approveJoinRequest(groupId: Long, userId: Long): Result<Unit>

    /** 가입 신청 거절(모더레이터 전용) — DELETE /api/groups/{id}/join-requests/{userId} */
    suspend fun rejectJoinRequest(groupId: Long, userId: Long): Result<Unit>
}
