package kr.hhp227.storygroup.shared

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kr.hhp227.storygroup.shared.domain.model.NetworkAlertState
import kr.hhp227.storygroup.shared.domain.repository.NetworkStatusRepository
import kr.hhp227.storygroup.shared.domain.usecase.ObserveNetworkAlertStateUseCase
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveNetworkAlertStateUseCaseTest {
    private val connection = MutableSharedFlow<Boolean>()

    private val useCase = ObserveNetworkAlertStateUseCase(object : NetworkStatusRepository {
        override fun observeIsConnected(): Flow<Boolean> = connection
    })

    @Test
    fun firstOnlineEmitsHidden() = runTest {
        val states = mutableListOf<NetworkAlertState>()

        backgroundScope.launch { useCase().collect { states += it } }
        runCurrent()
        connection.emit(true)
        runCurrent()

        assertEquals(listOf(NetworkAlertState.hidden), states)
    }

    @Test
    fun offlineEmitsOfflineOnce() = runTest {
        val states = mutableListOf<NetworkAlertState>()

        backgroundScope.launch { useCase().collect { states += it } }
        runCurrent()
        connection.emit(false)
        runCurrent()
        // distinctUntilChanged — 같은 값 반복은 무시된다
        connection.emit(false)
        runCurrent()

        assertEquals(listOf(NetworkAlertState.offline), states)
    }

    @Test
    fun recoveryShowsRecoveredThenHidesAfterDelay() = runTest {
        val states = mutableListOf<NetworkAlertState>()

        backgroundScope.launch { useCase().collect { states += it } }
        runCurrent()
        connection.emit(false)
        runCurrent()
        connection.emit(true)
        runCurrent()

        assertEquals(listOf(NetworkAlertState.offline, NetworkAlertState.recovered), states)

        advanceTimeBy(1_800)
        runCurrent()

        assertEquals(
            listOf(NetworkAlertState.offline, NetworkAlertState.recovered, NetworkAlertState.hidden),
            states
        )
    }
}
