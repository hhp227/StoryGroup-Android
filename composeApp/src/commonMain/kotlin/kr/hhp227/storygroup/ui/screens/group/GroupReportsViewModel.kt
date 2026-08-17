package kr.hhp227.storygroup.ui.screens.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.shared.domain.model.PostReport
import kr.hhp227.storygroup.shared.domain.model.ReportStatus
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupReportsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.ProcessGroupReportUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel

/**
 * 그룹 신고함(모더레이터 전용) — 웹 /groups/[id]/reports 미러. 필터(대기중/전체)와
 * 확인/기각 처리. 처리 성공은 대기중 필터에선 행 제거, 전체에선 상태만 갱신(웹과 동일).
 * 화면 전환(게시글 상세)은 화면 콜백 직행이라 EVENT는 Nothing.
 * iosApp GroupReportsViewModel.swift와 1:1 미러
 */
class GroupReportsViewModel(
    val groupId: Long,
    private val getGroupReportsUseCase: GetGroupReportsUseCase,
    private val processGroupReportUseCase: ProcessGroupReportUseCase
) : ViewModel(), MviViewModel<GroupReportsViewModel.UiState, GroupReportsViewModel.Action, Nothing> {
    private val _uiState = MutableStateFlow(UiState())
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    override val event: Flow<Nothing> = emptyFlow()

    override fun onAction(action: Action) {
        when (action) {
            Action.Refresh -> refresh()
            is Action.SetFilter -> setFilter(action.filter)
            is Action.Process -> process(action.reportId, action.status)
            Action.DismissActionError -> _uiState.update { it.copy(actionError = null) }
        }
    }

    private fun refresh() {
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            val filter = _uiState.value.filter

            runCatching { getGroupReportsUseCase(groupId, filter) }
                .onSuccess { reports ->
                    _uiState.update { it.copy(isLoading = false, reports = reports) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoading = false, loadError = e.message ?: "신고 목록을 불러오지 못했습니다.")
                    }
                }
        }
    }

    /** 필터 전환 — 목록을 비우고 다시 읽는다(웹의 key 리마운트 미러) */
    private fun setFilter(filter: ReportStatus?) {
        if (_uiState.value.filter == filter) return

        _uiState.update { it.copy(filter = filter, reports = null, isLoading = false, loadError = null) }
        refresh()
    }

    private fun process(reportId: Long, status: ReportStatus) {
        if (_uiState.value.busyReportId != null) return

        _uiState.update { it.copy(busyReportId = reportId, actionError = null) }
        viewModelScope.launch {
            runCatching { processGroupReportUseCase(groupId, reportId, status) }
                .onSuccess { updated ->
                    _uiState.update { state ->
                        // 대기중 필터에서는 처리된 행이 빠지고, 전체에서는 상태만 갱신된다(웹 미러)
                        val reports = state.reports?.mapNotNull { report ->
                            when {
                                report.id != reportId -> report
                                state.filter == ReportStatus.PENDING -> null
                                else -> updated
                            }
                        }

                        state.copy(busyReportId = null, reports = reports)
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(busyReportId = null, actionError = e.message ?: "신고 처리에 실패했습니다.") }
                }
        }
    }

    init {
        refresh()
    }

    data class UiState(
        // null=아직 로드 전 — 빈 목록(신고 없음)과 구분한다
        val reports: List<PostReport>? = null,
        val isLoading: Boolean = false,
        val loadError: String? = null,
        // 기본 대기중 — null이면 전체(웹 필터 칩 미러)
        val filter: ReportStatus? = ReportStatus.PENDING,
        // 처리 진행 중인 행 — 그 행의 버튼만 잠근다(웹 busyFor 미러)
        val busyReportId: Long? = null,
        val actionError: String? = null
    )

    sealed interface Action {
        data object Refresh : Action
        data class SetFilter(val filter: ReportStatus?) : Action
        data class Process(val reportId: Long, val status: ReportStatus) : Action
        data object DismissActionError : Action
    }
}
