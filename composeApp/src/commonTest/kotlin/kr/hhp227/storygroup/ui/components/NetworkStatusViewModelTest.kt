package kr.hhp227.storygroup.ui.components

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import kr.hhp227.storygroup.shared.domain.model.NetworkAlertState
import kr.hhp227.storygroup.shared.domain.repository.NetworkStatusRepository
import kr.hhp227.storygroup.shared.domain.usecase.ObserveNetworkAlertStateUseCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkStatusViewModelTest {
    private val connection = MutableSharedFlow<Boolean>()

    private val useCase = ObserveNetworkAlertStateUseCase(object : NetworkStatusRepository {
        override fun observeIsConnected(): Flow<Boolean> = connection
    })

    /** viewModelScope가 Main을 쓰므로 테스트 디스패처로 치환한다 */
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun startsHidden() = runTest {
        val viewModel = NetworkStatusViewModel(useCase)

        assertEquals(NetworkAlertState.hidden, viewModel.uiState.value.networkAlertState)
    }

    @Test
    fun offlineUpdatesUiState() = runTest {
        val viewModel = NetworkStatusViewModel(useCase)

        yield()
        connection.emit(false)
        yield()

        assertEquals(NetworkAlertState.offline, viewModel.uiState.value.networkAlertState)
    }
}
