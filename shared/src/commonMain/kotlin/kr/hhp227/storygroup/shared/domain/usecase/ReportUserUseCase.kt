package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.repository.UserRepository

/**
 * 사용자 신고 — 댓글에는 신고 API가 따로 없어 작성자를 신고한다(웹 UserActionMenu 미러).
 * 접수는 앱 운영자의 신고 관리로 가고(게시글 신고는 그룹 모더레이터 신고함), 사유는 선택이다.
 * 같은 대상에 대기중 신고가 이미 있으면 서버가 409로 막는다(메시지를 그대로 노출).
 */
class ReportUserUseCase(private val userRepository: UserRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(userId: Long, reason: String? = null) {
        userRepository.reportUser(userId, reason).getOrThrow()
    }
}
