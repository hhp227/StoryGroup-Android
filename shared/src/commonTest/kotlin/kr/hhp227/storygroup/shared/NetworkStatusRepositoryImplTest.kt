package kr.hhp227.storygroup.shared

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kr.hhp227.storygroup.shared.data.repository.NetworkStatusRepositoryImpl
import kr.hhp227.storygroup.shared.data.source.NetworkStatusDataSource
import kotlin.test.Test
import kotlin.test.assertEquals

class NetworkStatusRepositoryImplTest {
    @Test
    fun nullDataSourceIsAlwaysConnected() = runTest {
        val repository = NetworkStatusRepositoryImpl(null)

        assertEquals(listOf(true), repository.observeIsConnected().toList())
    }

    @Test
    fun delegatesToDataSource() = runTest {
        val repository = NetworkStatusRepositoryImpl(object : NetworkStatusDataSource {
            override fun observeIsConnected(): Flow<Boolean> = flowOf(false)
        })

        assertEquals(listOf(false), repository.observeIsConnected().toList())
    }
}
