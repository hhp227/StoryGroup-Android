package kr.hhp227.storygroup.shared.domain.repository

import kr.hhp227.storygroup.shared.domain.model.PushPlatform

interface PushTokenRepository {
    suspend fun register(token: String, platform: PushPlatform): Result<Unit>
    suspend fun unregister(token: String): Result<Unit>
}
