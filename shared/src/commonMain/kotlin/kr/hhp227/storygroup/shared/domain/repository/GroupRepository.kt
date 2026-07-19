package kr.hhp227.storygroup.shared.domain.repository

import kr.hhp227.storygroup.shared.domain.model.Group
import kr.hhp227.storygroup.shared.domain.model.GroupMember

interface GroupRepository {
    /** 내가 가입한 그룹 목록(라운지 포함) — GET /api/groups */
    suspend fun getMyGroups(): Result<List<Group>>

    /** 그룹 단건 조회(상세 진입 시 신선화) — GET /api/groups/{id} */
    suspend fun getGroup(groupId: Long): Result<Group>

    /** 그룹 멤버 목록 — GET /api/groups/{id}/members */
    suspend fun getMembers(groupId: Long): Result<List<GroupMember>>
}
