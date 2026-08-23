package kr.hhp227.storygroup.shared.data.source

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kr.hhp227.storygroup.shared.data.network.dto.UnifiedSearchResponse

/** 통합검색 원격 소스 — 전송·DTO만 담당, 예외는 그대로 던진다(Result 래핑·도메인 매핑은 리포지토리 몫) */
interface SearchRemoteDataSource {
    suspend fun search(query: String): UnifiedSearchResponse
}

class SearchRemoteDataSourceImpl(private val client: HttpClient) : SearchRemoteDataSource {
    override suspend fun search(query: String): UnifiedSearchResponse =
        client.get("/api/search") {
            parameter("query", query)
        }.body()
}
