package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.PushPlatform
import kr.hhp227.storygroup.shared.domain.repository.PushTokenRepository

/** FCM 토큰 서버 등록 — 멱등(upsert)이라 로그인·토큰 갱신 때마다 호출해도 무해 */
class RegisterPushTokenUseCase(private val pushTokenRepository: PushTokenRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(token: String, platform: PushPlatform) {
        pushTokenRepository.register(token, platform).getOrThrow()
    }
}
