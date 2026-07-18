package kr.hhp227.storygroup.shared.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kr.hhp227.storygroup.shared.data.network.dto.GroupResponse
import kr.hhp227.storygroup.shared.domain.model.Group
import kr.hhp227.storygroup.shared.domain.model.GroupJoinType
import kr.hhp227.storygroup.shared.domain.model.GroupRole
import kr.hhp227.storygroup.shared.domain.repository.GroupRepository

class GroupRepositoryImpl(private val client: HttpClient) : GroupRepository {

    override suspend fun getMyGroups(): Result<List<Group>> =
        runCatching { client.get("/api/groups").body<List<GroupResponse>>().map { it.toDomain() } }
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
