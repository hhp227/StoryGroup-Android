package kr.hhp227.storygroup.shared.data.repository

import app.cash.paging.Pager
import app.cash.paging.PagingData
import app.cash.paging.filter
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kr.hhp227.storygroup.shared.data.network.dto.DiscoverGroupResponse
import kr.hhp227.storygroup.shared.data.network.dto.ErrorResponse
import kr.hhp227.storygroup.shared.data.network.dto.GroupPhotoResponse
import kr.hhp227.storygroup.shared.data.network.dto.GroupResponse
import kr.hhp227.storygroup.shared.data.network.dto.InviteResponse
import kr.hhp227.storygroup.shared.data.network.dto.JoinRequestResponse
import kr.hhp227.storygroup.shared.data.network.dto.MemberResponse
import kr.hhp227.storygroup.shared.data.network.dto.PostReportResponse
import kr.hhp227.storygroup.shared.data.paging.PagePagingConfig
import kr.hhp227.storygroup.shared.data.paging.PagePagingSource
import kr.hhp227.storygroup.shared.data.source.GroupRemoteDataSource
import kr.hhp227.storygroup.shared.domain.model.DiscoverGroup
import kr.hhp227.storygroup.shared.domain.model.DiscoverSort
import kr.hhp227.storygroup.shared.domain.model.Group
import kr.hhp227.storygroup.shared.domain.model.GroupInvite
import kr.hhp227.storygroup.shared.domain.model.GroupJoinRequest
import kr.hhp227.storygroup.shared.domain.model.GroupJoinType
import kr.hhp227.storygroup.shared.domain.model.GroupMember
import kr.hhp227.storygroup.shared.domain.model.GroupMembershipStatus
import kr.hhp227.storygroup.shared.domain.model.GroupPhoto
import kr.hhp227.storygroup.shared.domain.model.GroupPhotoMediaType
import kr.hhp227.storygroup.shared.domain.model.GroupRole
import kr.hhp227.storygroup.shared.domain.model.JoinGroupResult
import kr.hhp227.storygroup.shared.domain.model.JoinResult
import kr.hhp227.storygroup.shared.domain.model.PostReport
import kr.hhp227.storygroup.shared.domain.model.ReportStatus
import kr.hhp227.storygroup.shared.domain.repository.GroupRepository

class GroupRepositoryImpl(private val groupRemoteDataSource: GroupRemoteDataSource) : GroupRepository {

    override fun getMyGroupsPagingData(): Flow<PagingData<Group>> =
        Pager(PagePagingConfig) {
            PagePagingSource { page, size ->
                groupRemoteDataSource.getMyGroups(page, size).map { it.toDomain() }
            }
        }.flow
            // 라운지는 홈 탭이 담당 — 웹 내 그룹 목록과 동일하게 목록에서 제외
            .map { pagingData -> pagingData.filter { !it.isLounge } }

    override suspend fun getMyGroups(): Result<List<Group>> =
        runCatching { groupRemoteDataSource.getMyGroups().map { it.toDomain() } }

    override suspend fun getGroup(groupId: Long): Result<Group> =
        runCatching { groupRemoteDataSource.getGroup(groupId).toDomain() }

    override suspend fun getMembers(groupId: Long): Result<List<GroupMember>> =
        runCatching {
            groupRemoteDataSource.getMembers(groupId).map { it.toDomain() }
        }

    override fun getGroupPhotosPagingData(groupId: Long): Flow<PagingData<GroupPhoto>> =
        Pager(PagePagingConfig) {
            PagePagingSource { page, size ->
                groupRemoteDataSource.getGroupPhotos(groupId, page, size).photos.map { it.toDomain() }
            }
        }.flow

    override suspend fun createGroup(
        name: String,
        description: String?,
        image: String?,
        joinType: GroupJoinType
    ): Result<Group> = runCatching {
        groupRemoteDataSource.createGroup(name = name, description = description, image = image, joinType = joinType.name).toDomain()
    }

    override fun getDiscoverGroupsPagingData(query: String, sort: DiscoverSort): Flow<PagingData<DiscoverGroup>> =
        Pager(PagePagingConfig) {
            PagePagingSource { page, size ->
                groupRemoteDataSource.getDiscoverGroups(query, sort.wire, page, size).map { it.toDomain() }
            }
        }.flow

    override suspend fun joinGroup(groupId: Long): Result<JoinGroupResult> =
        runCatching {
            val response = groupRemoteDataSource.joinGroup(groupId)

            JoinGroupResult(
                status = JoinResult.entries.first { it.name == response.status },
                group = response.group?.toDomain()
            )
        }

    override suspend fun cancelJoinRequest(groupId: Long): Result<Unit> =
        runCatching {
            groupRemoteDataSource.cancelJoinRequest(groupId)
        }

    override suspend fun getMyJoinRequestedGroups(): Result<List<DiscoverGroup>> =
        runCatching {
            groupRemoteDataSource.getMyJoinRequestedGroups().map { it.toDomain() }
        }

    override suspend fun getJoinRequests(groupId: Long): Result<List<GroupJoinRequest>> =
        runCatching {
            groupRemoteDataSource.getJoinRequests(groupId).map { it.toDomain() }
        }

    override suspend fun approveJoinRequest(groupId: Long, userId: Long): Result<Unit> =
        runCatching {
            groupRemoteDataSource.approveJoinRequest(groupId, userId)
        }

    override suspend fun rejectJoinRequest(groupId: Long, userId: Long): Result<Unit> =
        runCatching {
            groupRemoteDataSource.rejectJoinRequest(groupId, userId)
        }

    override suspend fun createInvite(groupId: Long, maxUses: Int?, expiresInDays: Int?): Result<GroupInvite> =
        runCatching {
            groupRemoteDataSource.createInvite(groupId, maxUses, expiresInDays).toDomain()
        }

    override suspend fun joinByCode(code: String): Result<Group> =
        runCatching {
            try {
                groupRemoteDataSource.joinByCode(code).toDomain()
            } catch (e: ClientRequestException) {
                // 코드 오입력/만료가 일상 실패 경로 — Ktor 예외 원문 대신 서버 에러 본문의
                // 사용자 문구(INVALID_INVITE 등)를 그대로 보여준다(웹 ApiError.message 미러)
                val message = runCatching { e.response.body<ErrorResponse>().message }.getOrNull()
                throw IllegalStateException(message ?: "유효하지 않거나 만료된 초대 코드입니다.", e)
            }
        }

    override suspend fun updateGroup(
        groupId: Long,
        name: String,
        description: String?,
        image: String?,
        joinType: GroupJoinType?
    ): Result<Group> = runCatching {
        groupRemoteDataSource.updateGroup(
            groupId = groupId,
            name = name,
            description = description,
            image = image,
            joinType = joinType?.name
        ).toDomain()
    }

    override suspend fun deleteGroup(groupId: Long): Result<Unit> =
        runCatching {
            groupRemoteDataSource.deleteGroup(groupId)
        }

    override suspend fun leaveGroup(groupId: Long): Result<Unit> =
        runCatching {
            groupRemoteDataSource.leaveGroup(groupId)
        }

    override suspend fun getGroupReports(groupId: Long, status: ReportStatus?): Result<List<PostReport>> =
        runCatching {
            groupRemoteDataSource.getGroupReports(groupId, status?.name).map { it.toDomain() }
        }

    override suspend fun processGroupReport(groupId: Long, reportId: Long, status: ReportStatus): Result<PostReport> =
        runCatching {
            groupRemoteDataSource.processGroupReport(groupId, reportId, status.name).toDomain()
        }
}

// 백엔드 sort 파라미터는 소문자 wire 이름(recent|popular) — DiscoverSort.name과 표기가 달라 별도 매핑
private val DiscoverSort.wire: String
    get() = when (this) {
        DiscoverSort.RECENT -> "recent"
        DiscoverSort.POPULAR -> "popular"
    }

private fun GroupResponse.toDomain() = Group(
    id = id,
    name = name,
    description = description,
    image = image,
    // 미래에 서버가 값을 추가해도 목록 조회가 통째로 깨지지 않게 모르는 값은 기본값으로 흡수
    joinType = GroupJoinType.entries.firstOrNull { it.name == joinType } ?: GroupJoinType.AUTO_APPROVE,
    myRole = GroupRole.entries.firstOrNull { it.name == myRole } ?: GroupRole.MEMBER,
    createdAt = createdAt,
    isLounge = isLounge
)

private fun MemberResponse.toDomain() = GroupMember(
    userId = userId,
    name = name,
    profileImg = profileImg,
    role = GroupRole.entries.firstOrNull { it.name == role } ?: GroupRole.MEMBER,
    joinedAt = joinedAt
)

private fun JoinRequestResponse.toDomain() = GroupJoinRequest(
    userId = userId,
    name = name,
    profileImg = profileImg,
    requestedAt = requestedAt
)

private fun InviteResponse.toDomain() = GroupInvite(
    code = code,
    maxUses = maxUses,
    expiresAt = expiresAt
)

private fun DiscoverGroupResponse.toDomain() = DiscoverGroup(
    id = id,
    name = name,
    description = description,
    image = image,
    joinType = GroupJoinType.entries.firstOrNull { it.name == joinType } ?: GroupJoinType.AUTO_APPROVE,
    memberCount = memberCount,
    membership = GroupMembershipStatus.entries.firstOrNull { it.name == membership } ?: GroupMembershipStatus.NONE,
    createdAt = createdAt
)

private fun GroupPhotoResponse.toDomain() = GroupPhoto(
    id = id,
    postId = postId,
    image = image,
    // 미지의 값은 IMAGE 폴백 — 서버가 종류를 늘려도 그리드가 죽지 않는다
    mediaType = if (mediaType.equals("video", ignoreCase = true)) GroupPhotoMediaType.VIDEO
        else GroupPhotoMediaType.IMAGE,
    userId = userId,
    authorName = authorName,
    createdAt = createdAt
)

private fun PostReportResponse.toDomain() = PostReport(
    id = id,
    postId = postId,
    postTextPreview = postTextPreview,
    postAuthorId = postAuthorId,
    postAuthorName = postAuthorName,
    reporterId = reporterId,
    reporterName = reporterName,
    reason = reason,
    // 미지의 값은 PENDING 폴백 — 서버가 상태를 늘려도 목록이 죽지 않는다
    status = ReportStatus.entries.firstOrNull { it.name == status } ?: ReportStatus.PENDING,
    createdAt = createdAt,
    processedAt = processedAt
)
