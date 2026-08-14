package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.SearchResults
import kr.hhp227.storygroup.shared.domain.repository.SearchRepository

/** 홈 통합검색 — 5섹션 전부(users만 쓰는 친구 탭 SearchUsersUseCase와 별개) */
class SearchUseCase(
    private val searchRepository: SearchRepository
) {
    @Throws(Exception::class)
    suspend operator fun invoke(query: String): SearchResults =
        searchRepository.search(query).getOrThrow()
}
