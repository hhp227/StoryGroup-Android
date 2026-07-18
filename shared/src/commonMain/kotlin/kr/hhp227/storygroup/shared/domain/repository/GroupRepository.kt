package kr.hhp227.storygroup.shared.domain.repository

import kr.hhp227.storygroup.shared.domain.model.Group

interface GroupRepository {
    /** 내가 가입한 그룹 목록(라운지 포함) — GET /api/groups */
    suspend fun getMyGroups(): Result<List<Group>>
}
