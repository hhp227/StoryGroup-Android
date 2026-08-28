package kr.hhp227.storygroup.shared.data.repository

import kr.hhp227.storygroup.shared.data.source.PushTokenRemoteDataSource
import kr.hhp227.storygroup.shared.domain.model.PushPlatform
import kr.hhp227.storygroup.shared.domain.repository.PushTokenRepository

class PushTokenRepositoryImpl(
    private val pushTokenRemoteDataSource: PushTokenRemoteDataSource
) : PushTokenRepository {
    override suspend fun register(token: String, platform: PushPlatform): Result<Unit> =
        runCatching { pushTokenRemoteDataSource.register(token, platform.name) }

    override suspend fun unregister(token: String): Result<Unit> =
        runCatching { pushTokenRemoteDataSource.unregister(token) }
}
