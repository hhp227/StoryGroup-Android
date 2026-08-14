package kr.hhp227.storygroup.shared.domain.repository

import kr.hhp227.storygroup.shared.domain.model.SearchResults

interface SearchRepository {
    /**
     * 통합검색 — GET /api/search 5섹션 전부 소비(친구 탭 FriendRepository.searchUsers는 users만).
     * limit은 생략(서버 기본 10, 1..20 클램프) — 5섹션이 쌓이는 화면이라 섹션당 10이면 충분.
     * 빈 검색어는 서버가 400 — 호출 전에 trim 빈 문자열을 걸러야 한다(VM 몫).
     */
    suspend fun search(query: String): Result<SearchResults>
}
