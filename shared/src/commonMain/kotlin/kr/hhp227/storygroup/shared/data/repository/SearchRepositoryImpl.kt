package kr.hhp227.storygroup.shared.data.repository

import kr.hhp227.storygroup.shared.data.network.dto.FileSearchResponse
import kr.hhp227.storygroup.shared.data.network.dto.GroupSearchResponse
import kr.hhp227.storygroup.shared.data.network.dto.MessageSearchResponse
import kr.hhp227.storygroup.shared.data.network.dto.PostSearchResponse
import kr.hhp227.storygroup.shared.data.network.dto.UnifiedSearchResponse
import kr.hhp227.storygroup.shared.data.source.SearchRemoteDataSource
import kr.hhp227.storygroup.shared.domain.model.FileSearchHit
import kr.hhp227.storygroup.shared.domain.model.GroupSearchHit
import kr.hhp227.storygroup.shared.domain.model.MessageSearchHit
import kr.hhp227.storygroup.shared.domain.model.PostSearchHit
import kr.hhp227.storygroup.shared.domain.model.SearchResults
import kr.hhp227.storygroup.shared.domain.model.UserSearchResult
import kr.hhp227.storygroup.shared.domain.repository.SearchRepository

class SearchRepositoryImpl(private val searchRemoteDataSource: SearchRemoteDataSource) : SearchRepository {
    override suspend fun search(query: String): Result<SearchResults> =
        runCatching {
            searchRemoteDataSource.search(query).toDomain()
        }
}

private fun UnifiedSearchResponse.toDomain() = SearchResults(
    groups = groups.map { it.toDomain() },
    posts = posts.map { it.toDomain() },
    files = files.map { it.toDomain() },
    messages = messages.map { it.toDomain() },
    // users 섹션은 친구 탭과 같은 도메인 모델 — 친구 추가/해제 유스케이스를 그대로 쓴다
    users = users.map { UserSearchResult(it.id, it.name, it.profileImg, it.statusMessage) }
)

private fun GroupSearchResponse.toDomain() = GroupSearchHit(id, name, image, description)

private fun PostSearchResponse.toDomain() = PostSearchHit(id, groupId, groupName, authorName, text, createdAt)

private fun FileSearchResponse.toDomain() = FileSearchHit(id, groupId, groupName, name, url, createdAt)

private fun MessageSearchResponse.toDomain() =
    MessageSearchHit(id, chatRoomId, groupId, groupName, authorName, text, createdAt)
