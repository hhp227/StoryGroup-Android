package kr.hhp227.storygroup.shared.data.source

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Android 연결 감지 — 기본 네트워크 콜백(ConCafe 이식, Koin 대신 생성자 Context 주입).
 * Doze·화면 잠금 복귀 시 콜백 누락 대비 5초 폴링 보완. INTERNET+VALIDATED 둘 다 있어야 온라인.
 */
class AndroidNetworkStatusDataSource(context: Context) : NetworkStatusDataSource {
    private val appContext = context.applicationContext

    override fun observeIsConnected(): Flow<Boolean> {
        return callbackFlow {
            val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    trySend(connectivityManager.isCurrentlyConnected())
                }

                override fun onLost(network: Network) {
                    trySend(connectivityManager.isCurrentlyConnected())
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    trySend(connectivityManager.isCurrentlyConnected())
                }

                override fun onUnavailable() {
                    trySend(false)
                }
            }

            trySend(connectivityManager.isCurrentlyConnected())
            connectivityManager.registerDefaultNetworkCallback(callback)

            val pollingJob = launch {
                while (isActive) {
                    delay(POLL_INTERVAL_MS)
                    trySend(connectivityManager.isCurrentlyConnected())
                }
            }

            awaitClose {
                pollingJob.cancel()
                connectivityManager.unregisterNetworkCallback(callback)
            }
        }.distinctUntilChanged()
    }

    private companion object {
        const val POLL_INTERVAL_MS = 5_000L
    }
}

private fun ConnectivityManager.isCurrentlyConnected(): Boolean {
    val capabilities = getNetworkCapabilities(activeNetwork)
    val hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    val hasValidated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    return hasInternet && hasValidated
}
