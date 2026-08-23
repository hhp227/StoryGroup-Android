package kr.hhp227.storygroup.shared.domain.repository

import kotlinx.coroutines.flow.Flow

/** OS 수준 인터넷 연결 상태 스트림 — 개별 요청 실패와 무관하게 연결 여부만 관찰한다 */
interface NetworkStatusRepository {
    fun observeIsConnected(): Flow<Boolean>
}
