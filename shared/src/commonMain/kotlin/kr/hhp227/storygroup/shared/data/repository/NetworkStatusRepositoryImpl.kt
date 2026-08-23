package kr.hhp227.storygroup.shared.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kr.hhp227.storygroup.shared.data.source.NetworkStatusDataSource
import kr.hhp227.storygroup.shared.domain.repository.NetworkStatusRepository

/** 데이터소스 null이면 항상 온라인(배너 숨김) — Preview·미주입 폴백(imageCompressor null=무압축 관례) */
class NetworkStatusRepositoryImpl(
    private val networkStatusDataSource: NetworkStatusDataSource?
) : NetworkStatusRepository {
    override fun observeIsConnected(): Flow<Boolean> {
        return networkStatusDataSource?.observeIsConnected() ?: flowOf(true)
    }
}
