package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.IceServer
import kr.hhp227.storygroup.shared.domain.repository.RtcRepository

/** ICE 서버 구성 조회 — 실패 시 호출 측이 STUN 폴백을 쓴다(조회 실패가 통화를 막으면 안 된다, 웹 미러) */
class GetIceServersUseCase(
    private val rtcRepository: RtcRepository
) {
    @Throws(Exception::class)
    suspend operator fun invoke(): List<IceServer> =
        rtcRepository.getIceServers().getOrThrow()
}
