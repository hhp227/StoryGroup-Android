package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.repository.PushTokenRepository

/** 로그아웃 시 토큰 해제 — 실패해도 로그아웃은 진행(best effort, 서버 upsert 이관이 최종 방어선) */
class UnregisterPushTokenUseCase(private val pushTokenRepository: PushTokenRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(token: String) {
        pushTokenRepository.unregister(token).getOrThrow()
    }
}
