package kr.hhp227.storygroup.shared.data.repository

import app.cash.paging.Pager
import app.cash.paging.PagingData
import app.cash.paging.filter
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kr.hhp227.storygroup.shared.data.network.dto.GroupResponse
import kr.hhp227.storygroup.shared.data.paging.PagePagingConfig
import kr.hhp227.storygroup.shared.data.paging.PagePagingSource
import kr.hhp227.storygroup.shared.data.network.dto.MemberResponse
import kr.hhp227.storygroup.shared.domain.model.Group
import kr.hhp227.storygroup.shared.domain.model.GroupJoinType
import kr.hhp227.storygroup.shared.domain.model.GroupMember
import kr.hhp227.storygroup.shared.domain.model.GroupRole
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
