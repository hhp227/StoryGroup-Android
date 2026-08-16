package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.PostReport
import kr.hhp227.storygroup.shared.domain.model.ReportStatus
import kr.hhp227.storygroup.shared.domain.repository.GroupRepository

/**
 * 그룹 신고함 목록(모더레이터 전용) — 웹 /groups/[id]/reports 미러.
 * status null=전체, PENDING=대기중 필터(화면 기본값). 권한 없는 호출은 서버가 403으로 거른다.
 */
class GetGroupReportsUseCase(private val groupRepository: GroupRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(groupId: Long, status: ReportStatus? = null): List<PostReport> =
        groupRepository.getGroupReports(groupId, status).getOrThrow()
}
