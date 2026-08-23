package kr.hhp227.storygroup.shared.data.source

import kotlinx.coroutines.flow.Flow

/**
 * 플랫폼 연결 감지 소스 — 구현은 플랫폼 소스셋(Android=ConnectivityManager,
 * iOS=SCNetworkReachability, Desktop=소켓 폴링)에 있고, 진입점이 컨테이너에 주입한다.
 */
interface NetworkStatusDataSource {
    fun observeIsConnected(): Flow<Boolean>
}
