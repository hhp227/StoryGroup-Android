package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.repository.UserRepository

/**
 * 사용자 차단 — 그 사용자의 글·댓글·채팅이 내 화면에서 숨겨지고 DM이 양방향으로 막힌다.
 * 차단하면 보고 있던 글도 목록에서 사라지므로, 호출부는 삭제와 같은 복귀·갱신 경로를 탄다.
 */
class BlockUserUseCase(private val userRepository: UserRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(userId: Long) {
        userRepository.blockUser(userId).getOrThrow()
    }
}
