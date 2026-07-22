package kr.hhp227.storygroup.shared.data.repository

import app.cash.paging.Pager
import app.cash.paging.PagingData
import app.cash.paging.filter
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kr.hhp227.storygroup.shared.data.network.dto.CreateGroupRequest
import kr.hhp227.storygroup.shared.data.network.dto.DiscoverGroupResponse
import kr.hhp227.storygroup.shared.data.network.dto.GroupResponse
import kr.hhp227.storygroup.shared.data.network.dto.JoinGroupResponse
import kr.hhp227.storygroup.shared.data.network.dto.JoinRequestResponse
import kr.hhp227.storygroup.shared.data.network.dto.MemberResponse
import kr.hhp227.storygroup.shared.data.paging.PagePagingConfig
import kr.hhp227.storygroup.shared.data.paging.PagePagingSource
import kr.hhp227.storygroup.shared.domain.model.DiscoverGroup
import kr.hhp227.storygroup.shared.domain.model.DiscoverSort
import kr.hhp227.storygroup.shared.domain.model.Group
import kr.hhp227.storygroup.shared.domain.model.GroupJoinRequest
import kr.hhp227.storygroup.shared.domain.model.GroupJoinType
import kr.hhp227.storygroup.shared.domain.model.GroupMember
import kr.hhp227.storygroup.shared.domain.model.GroupMembershipStatus
import kr.hhp227.storygroup.shared.domain.model.GroupRole
import kr.hhp227.storygroup.shared.domain.model.JoinGroupResult
import kr.hhp227.storygroup.shared.domain.model.JoinResult
import kr.hhp227.storygroup.shared.domain.repository.GroupRepository

class GroupRepositoryImpl(private val client: HttpClient) : GroupRepository {

    override fun getMyGroupsPagingData(): Flow<PagingData<Group>> =
        Pager(PagePagingConfig) {
            PagePagingSource { page, size ->
                client.get("/api/groups") {
                    parameter("page", page)
                    parameter("size", size)
                }.body<List<GroupResponse>>().map { it.toDomain() }
            }
        }.flow
            // 라운지는 홈 탭이 담당 — 웹 내 그룹 목록과 동일하게 목록에서 제외
            .map { pagingData -> pagingData.filter { !it.isLounge } }

    override suspend fun getMyGroups(): Result<List<Group>> =
        runCatching { client.get("/api/groups").body<List<GroupResponse>>().map { it.toDomain() } }

    override suspend fun getGroup(groupId: Long): Result<Group> =
        runCatching { client.get("/api/groups/$groupId").body<GroupResponse>().toDomain() }

    override suspend fun getMembers(groupId: Long): Result<List<GroupMember>> =
        runCatching {
            client.get("/api/groups/$groupId/members").body<List<MemberResponse>>().map { it.toDomain() }
        }

    override suspend fun createGroup(
        name: String,
        description: String?,
        image: String?,
        joinType: GroupJoinType
    ): Result<Group> = runCatching {
        client.post("/api/groups") {
            contentType(ContentType.Application.Json)
            setBody(CreateGroupRequest(name = name, description = description, image = image, joinType = joinType.name))
        }.body<GroupResponse>().toDomain()
    }

    override fun getDiscoverGroupsPagingData(query: String, sort: DiscoverSort): Flow<PagingData<DiscoverGroup>> =
        Pager(PagePagingConfig) {
            PagePagingSource { page, size ->
                client.get("/api/groups/discover") {
                    parameter("query", query)
                    parameter("sort", sort.wire)
                    parameter("page", page)
                    parameter("size", size)
                }.body<List<DiscoverGroupResponse>>().map { it.toDomain() }
            }
        }.flow

    override suspend fun joinGroup(groupId: Long): Result<JoinGroupResult> =
        runCatching {
            val response = client.post("/api/groups/$groupId/join").body<JoinGroupResponse>()

            JoinGroupResult(
                status = JoinResult.entries.first { it.name == response.status },
                group = response.group?.toDomain()
            )
        }

    override suspend fun cancelJoinRequest(groupId: Long): Result<Unit> =
        runCatching {
            client.delete("/api/groups/$groupId/join")
            Unit
        }

    override suspend fun getJoinRequests(groupId: Long): Result<List<GroupJoinRequest>> =
        runCatching {
            client.get("/api/groups/$groupId/join-requests").body<List<JoinRequestResponse>>().map { it.toDomain() }
        }

    override suspend fun approveJoinRequest(groupId: Long, userId: Long): Result<Unit> =
        runCatching {
            client.post("/api/groups/$groupId/join-requests/$userId/approve")
            Unit
        }

    override suspend fun rejectJoinRequest(groupId: Long, userId: Long): Result<Unit> =
        runCatching {
            client.delete("/api/groups/$groupId/join-requests/$userId")
            Unit
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
