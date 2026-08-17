package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.PostReport
import kr.hhp227.storygroup.shared.domain.model.ReportStatus
import kr.hhp227.storygroup.shared.domain.repository.GroupRepository

/**
 * 신고 처리(모더레이터 전용) — 확인(RESOLVED)/기각(DISMISSED) 기록만 남기고 처리된 행을 돌려준다.
 * 게시글 삭제 등 실제 조치는 게시글 화면의 기존 기능으로 한다(웹 미러). 재처리(확인↔기각) 가능.
 */
class ProcessGroupReportUseCase(private val groupRepository: GroupRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(groupId: Long, reportId: Long, status: ReportStatus): PostReport =
        groupRepository.processGroupReport(groupId, reportId, status).getOrThrow()
}
