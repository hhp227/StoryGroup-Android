package kr.hhp227.storygroup.shared.domain.repository

import app.cash.paging.PagingData
import kotlinx.coroutines.flow.Flow
import kr.hhp227.storygroup.shared.domain.model.DiscoverGroup
import kr.hhp227.storygroup.shared.domain.model.DiscoverSort
import kr.hhp227.storygroup.shared.domain.model.Group
import kr.hhp227.storygroup.shared.domain.model.GroupInvite
import kr.hhp227.storygroup.shared.domain.model.GroupJoinRequest
import kr.hhp227.storygroup.shared.domain.model.GroupJoinType
import kr.hhp227.storygroup.shared.domain.model.GroupMember
import kr.hhp227.storygroup.shared.domain.model.GroupPhoto
import kr.hhp227.storygroup.shared.domain.model.JoinGroupResult
import kr.hhp227.storygroup.shared.domain.model.PostReport
import kr.hhp227.storygroup.shared.domain.model.ReportStatus

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

    /** 그룹 앨범(게시글 첨부 파생 뷰) Paging 스트림 — 최신 게시글 순 */
    fun getGroupPhotosPagingData(groupId: Long): Flow<PagingData<GroupPhoto>>

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

    /** 내가 가입 신청중(PENDING)인 그룹 목록 — GET /api/groups/join-requests/mine, 탐색 응답 재사용(membership=PENDING) */
    suspend fun getMyJoinRequestedGroups(): Result<List<DiscoverGroup>>

    /** 승인 대기 가입 신청 목록(모더레이터 전용) — GET /api/groups/{id}/join-requests */
    suspend fun getJoinRequests(groupId: Long): Result<List<GroupJoinRequest>>

    /** 가입 신청 승인(모더레이터 전용) — POST /api/groups/{id}/join-requests/{userId}/approve */
    suspend fun approveJoinRequest(groupId: Long, userId: Long): Result<Unit>

    /** 가입 신청 거절(모더레이터 전용) — DELETE /api/groups/{id}/join-requests/{userId} */
    suspend fun rejectJoinRequest(groupId: Long, userId: Long): Result<Unit>

    /** 초대코드 생성(모더레이터 전용) — POST /api/groups/{id}/invites, null 제한은 무제한/무기한 */
    suspend fun createInvite(groupId: Long, maxUses: Int?, expiresInDays: Int?): Result<GroupInvite>

    /** 초대코드로 가입 — POST /api/groups/join/{code}, 승인제 그룹이라도 즉시 MEMBER로 가입된다 */
    suspend fun joinByCode(code: String): Result<Group>

    /**
     * 그룹 정보 수정(OWNER 전용) — PATCH /api/groups/{id}.
     * ⚠️name/description/image는 전체 교체 계약(null=null로 덮어씀) — 폼이 기존 값을 항상 실어 보낸다.
     * joinType null=기존 유지(라운지는 서버가 가입 방식 자체를 안 바꾼다).
     */
    suspend fun updateGroup(
        groupId: Long,
        name: String,
        description: String?,
        image: String?,
        joinType: GroupJoinType?
    ): Result<Group>

    /** 그룹 삭제(OWNER 전용, 라운지 불가) — DELETE /api/groups/{id} */
    suspend fun deleteGroup(groupId: Long): Result<Unit>

    /** 그룹 나가기(멤버/부방장 — OWNER·라운지는 서버가 거부) — POST /api/groups/{id}/leave */
    suspend fun leaveGroup(groupId: Long): Result<Unit>

    /**
     * 그룹 신고함 목록(모더레이터 전용) — GET /api/groups/{id}/reports, status null=전체.
     * 멤버가 신고한 게시글 요약이 내려온다(웹 /groups/[id]/reports 미러).
     */
    suspend fun getGroupReports(groupId: Long, status: ReportStatus? = null): Result<List<PostReport>>

    /**
     * 신고 처리(모더레이터 전용) — PATCH /api/groups/{id}/reports/{reportId}, 처리된 행을 돌려준다.
     * 확인(RESOLVED)/기각(DISMISSED)은 기록일 뿐 — 게시글 삭제 등 조치는 기존 기능으로 한다.
     */
    suspend fun processGroupReport(groupId: Long, reportId: Long, status: ReportStatus): Result<PostReport>
}
