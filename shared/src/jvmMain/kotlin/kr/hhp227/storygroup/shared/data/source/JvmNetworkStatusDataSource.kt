package kr.hhp227.storygroup.shared.data.source

import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

/**
 * Desktop 연결 감지 — OS 콜백 API가 없어 공용 DNS(1.1.1.1→8.8.8.8 폴백)로 TCP 연결을 5초 폴링한다
 * (ConCafe jvm은 항상 온라인 스텁이었음 — 여기선 실제 감지). 소켓 예외는 오프라인 판정으로 흡수.
 */
class JvmNetworkStatusDataSource : NetworkStatusDataSource {
    override fun observeIsConnected(): Flow<Boolean> {
        return flow {
            while (currentCoroutineContext().isActive) {
                emit(isCurrentlyConnected())
                delay(POLL_INTERVAL_MS)
            }
        }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)
    }

    private fun isCurrentlyConnected(): Boolean {
        return PROBE_ADDRESSES.any { (host, port) -> canConnect(host, port) }
    }

    private fun canConnect(host: String, port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private companion object {
        val PROBE_ADDRESSES = listOf("1.1.1.1" to 53, "8.8.8.8" to 53)
        const val CONNECT_TIMEOUT_MS = 1_500
        const val POLL_INTERVAL_MS = 5_000L
    }
}
