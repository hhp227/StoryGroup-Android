package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.BlockedUser
import kr.hhp227.storygroup.shared.domain.repository.UserRepository

/**
 * 내가 차단한 사용자 목록 — 멤버를 그리는 화면이 이 목록으로 차단 사용자를 걸러낸다.
 * 서버는 게시글·댓글·채팅만 걸러줄 뿐 그룹 멤버 목록에는 차단 사용자가 그대로 내려온다
 * (웹은 멤버 목록에 남기고 "차단 해제" 버튼을 다는 방식이라 앱과 화면 규칙이 다르다).
 */
class GetBlockedUsersUseCase(private val userRepository: UserRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(): List<BlockedUser> = userRepository.getBlockedUsers().getOrThrow()
}
