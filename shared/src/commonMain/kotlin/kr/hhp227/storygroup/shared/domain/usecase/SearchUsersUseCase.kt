package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.UserSearchResult
import kr.hhp227.storygroup.shared.domain.repository.FriendRepository

/**
 * 사용자 검색 — 통합검색의 users 섹션만 소비(같은 그룹 소속만 노출, 본인·차단 제외).
 * limit은 서버 최대치 20 고정 — Kotlin 기본 인자는 ObjC로 나가지 않아 파라미터로 두지 않는다.
 */
class SearchUsersUseCase(
    private val friendRepository: FriendRepository
) {
    @Throws(Exception::class)
    suspend operator fun invoke(query: String): List<UserSearchResult> =
        friendRepository.searchUsers(query, limit = 20).getOrThrow()
}
