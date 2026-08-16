package kr.hhp227.storygroup.ui.screens.group

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.hhp227.storygroup.di.LocalAppContainer
import kr.hhp227.storygroup.shared.domain.model.PostReport
import kr.hhp227.storygroup.shared.domain.model.ReportStatus
import kr.hhp227.storygroup.ui.components.SgCard
import kr.hhp227.storygroup.ui.components.SgEmptyState
import kr.hhp227.storygroup.ui.components.SgTopBar
import kr.hhp227.storygroup.ui.theme.SgTheme
import kr.hhp227.storygroup.ui.util.formatRelativeTime

/** 백스택 엔트리 스코프 VM — 화면이 default parameter로 선언(GroupDetail 패턴) */
@Composable
private fun groupReportsViewModel(groupId: Long): GroupReportsViewModel {
    val container = LocalAppContainer.current

    return viewModel(key = "group-reports-$groupId") {
        GroupReportsViewModel(
            groupId = groupId,
            getGroupReportsUseCase = container.getGroupReportsUseCase,
            processGroupReportUseCase = container.processGroupReportUseCase
        )
    }
}

/**
 * 그룹 신고함(모더레이터 전용) — 웹 /groups/[id]/reports 미러. 진입점은 그룹 설정 탭
 * "신고함" 메뉴(canModerate). 처리는 기록일 뿐이고 실제 조치(게시글 삭제 등)는
 * 게시글 상세의 기존 기능으로 한다. iosApp GroupReportsView.swift와 1:1 미러
 */
@Composable
fun GroupReportsScreen(
    groupId: Long,
    onBack: () -> Unit,
    onOpenPostDetail: (postId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GroupReportsViewModel = groupReportsViewModel(groupId)
) {
    val uiState by viewModel.uiState.collectAsState()
    val onAction = viewModel::onAction
    val sg = SgTheme.colors

    Column(modifier.fillMaxSize()) {
        SgTopBar(
            title = "신고함",
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                }
            }
        )
        Text(
            "멤버가 신고한 게시글입니다. 확인/기각은 처리 기록이며, 게시글 삭제 등 조치는 게시글에서 직접 합니다.",
            style = SgTheme.typography.bodySmall,
            color = sg.inkFaint,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                label = "대기중",
                active = uiState.filter == ReportStatus.PENDING,
                onClick = { onAction(GroupReportsViewModel.Action.SetFilter(ReportStatus.PENDING)) }
            )
            FilterChip(
                label = "전체",
                active = uiState.filter == null,
                onClick = { onAction(GroupReportsViewModel.Action.SetFilter(null)) }
            )
        }
        val reports = uiState.reports

        when {
            reports == null && uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = sg.accent)
            }
            reports == null -> Column(
                modifier = Modifier.fillMaxSize().padding(vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(uiState.loadError ?: "신고 목록을 불러오지 못했습니다.", style = SgTheme.typography.bodyMedium, color = sg.rust)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { onAction(GroupReportsViewModel.Action.Refresh) }) {
                    Text("다시 시도", color = sg.accent)
                }
            }
            reports.isEmpty() -> SgEmptyState(
                title = if (uiState.filter == ReportStatus.PENDING) "대기중인 신고가 없습니다." else "신고 내역이 없습니다.",
                subtitle = "멤버가 게시글을 신고하면 여기로 접수됩니다.",
                modifier = Modifier.fillMaxSize()
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.actionError?.let { error ->
                    item(key = "action-error") {
                        Text(error, style = SgTheme.typography.bodySmall, color = sg.rust)
                    }
                }
                items(reports, key = { it.id }) { report ->
                    ReportCard(
                        report = report,
                        isBusy = uiState.busyReportId != null,
                        onOpenPost = { onOpenPostDetail(report.postId) },
                        onProcess = { status ->
                            onAction(GroupReportsViewModel.Action.Process(report.id, status))
                        }
                    )
                }
            }
        }
    }
}

/** 신고 한 행 — 상태 뱃지+일시·신고자, 게시글 요약(탭=상세), 사유, 대기중이면 처리 버튼(웹 카드 미러) */
@Composable
private fun ReportCard(
    report: PostReport,
    isBusy: Boolean,
    onOpenPost: () -> Unit,
    onProcess: (ReportStatus) -> Unit
) {
    val sg = SgTheme.colors
    val statusColor = when (report.status) {
        ReportStatus.PENDING -> sg.accent
        ReportStatus.RESOLVED -> sg.moss
        ReportStatus.DISMISSED -> sg.inkFaint
    }
    val statusLabel = when (report.status) {
        ReportStatus.PENDING -> "대기중"
        ReportStatus.RESOLVED -> "확인됨"
        ReportStatus.DISMISSED -> "기각됨"
    }

    SgCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(label = statusLabel, color = statusColor)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${formatRelativeTime(report.createdAt)} · ${report.reporterName}님 신고",
                    style = SgTheme.typography.labelSmall,
                    color = sg.inkFaint
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenPost),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text("${report.postAuthorName}님의 게시글", style = SgTheme.typography.bodySmall, color = sg.inkSoft)
                Text(
                    report.postTextPreview.ifEmpty { "(본문 없이 첨부만 있는 게시글)" },
                    style = SgTheme.typography.bodyMedium,
                    color = sg.ink
                )
            }
            if (!report.reason.isNullOrBlank()) {
                Text("신고 사유: ${report.reason}", style = SgTheme.typography.bodySmall, color = sg.inkSoft)
            }
            if (report.status == ReportStatus.PENDING) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onProcess(ReportStatus.RESOLVED) },
                        enabled = !isBusy,
                        shape = SgTheme.shapes.button,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = sg.inkSoft)
                    ) {
                        Text("확인 처리", style = SgTheme.typography.labelLarge)
                    }
                    TextButton(onClick = { onProcess(ReportStatus.DISMISSED) }, enabled = !isBusy) {
                        Text("기각", style = SgTheme.typography.labelLarge, color = sg.inkSoft)
                    }
                }
            }
        }
    }
}

/** 상태 pill — 웹 신고함의 색 테두리 뱃지 미러 */
@Composable
private fun StatusBadge(label: String, color: Color) {
    Text(
        label,
        style = SgTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier
            .border(1.dp, color, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 1.dp)
    )
}

/** 필터 칩 — 활성이면 accent 채움(웹 필터 칩 미러) */
@Composable
private fun FilterChip(label: String, active: Boolean, onClick: () -> Unit) {
    val sg = SgTheme.colors

    Text(
        label,
        style = SgTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = if (active) sg.onAccent else sg.inkSoft,
        modifier = Modifier
            .background(if (active) sg.accent else Color.Transparent, RoundedCornerShape(50))
            .border(1.dp, if (active) sg.accent else sg.stoneBorder, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    )
}
