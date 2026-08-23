package kr.hhp227.storygroup.shared.data.source

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import platform.CoreFoundation.CFRelease
import platform.SystemConfiguration.SCNetworkReachabilityCreateWithName
import platform.SystemConfiguration.SCNetworkReachabilityGetFlags
import platform.SystemConfiguration.kSCNetworkReachabilityFlagsConnectionRequired
import platform.SystemConfiguration.kSCNetworkReachabilityFlagsReachable

/** iOS 연결 감지 — SCNetworkReachability 2초 폴링(ConCafe 이식 — 검증된 코드라 NWPathMonitor 미채택) */
class IosNetworkStatusDataSource : NetworkStatusDataSource {
    override fun observeIsConnected(): Flow<Boolean> {
        return flow {
            while (currentCoroutineContext().isActive) {
                emit(isCurrentlyConnected())
                delay(POLL_INTERVAL_MS)
            }
        }
            .distinctUntilChanged()
            // 도메인명 reachability는 DNS 조회로 동기 블록될 수 있어 메인 스레드를 피한다
            .flowOn(Dispatchers.Default)
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun isCurrentlyConnected(): Boolean {
        // Create-rule CF 객체 — K/N은 자동 해제하지 않으므로 폴링마다 직접 release한다
        val reachability = SCNetworkReachabilityCreateWithName(null, REACHABILITY_HOST) ?: return false

        try {
            val flagsHolder = UIntArray(1)
            val didGetFlags = flagsHolder.usePinned { pinned ->
                SCNetworkReachabilityGetFlags(reachability, pinned.addressOf(0))
            }
            if (!didGetFlags) return false

            val flags = flagsHolder[0].toULong()
            val isReachable = (flags and kSCNetworkReachabilityFlagsReachable.toULong()) != 0uL
            val requiresConnection = (flags and kSCNetworkReachabilityFlagsConnectionRequired.toULong()) != 0uL
            return isReachable && !requiresConnection
        } finally {
            CFRelease(reachability)
        }
    }

    private companion object {
        const val REACHABILITY_HOST = "www.apple.com"
        const val POLL_INTERVAL_MS = 2_000L
    }
}
