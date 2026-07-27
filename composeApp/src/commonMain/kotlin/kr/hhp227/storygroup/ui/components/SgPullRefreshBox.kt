package kr.hhp227.storygroup.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kr.hhp227.storygroup.ui.theme.SgTheme

/**
 * 당겨서 새로고침 컨테이너 — M2 pullrefresh를 SG 톤(linen 서클+accent 스피너)으로 감싼 공용 래퍼.
 * refreshing은 화면이 Paging refresh LoadState로 판정해 넘긴다(로딩/에러를 LoadState로 그리는 관용구의 연장).
 * onRefresh가 null이면 제스처 없는 일반 Box로 동작한다 — SgCollapsingHeaderScaffold의 선택 파라미터용.
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SgPullRefreshBox(
    refreshing: Boolean,
    onRefresh: (() -> Unit)?,
    modifier: Modifier = Modifier,
    // 인디케이터가 내려오기 시작하는 상단 여백 — 핀 상단바 아래로 내릴 때 사용
    indicatorTopPadding: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val sg = SgTheme.colors
    val pullRefreshState = rememberPullRefreshState(refreshing, onRefresh ?: {})

    Box(if (onRefresh != null) modifier.pullRefresh(pullRefreshState) else modifier) {
        content()
        if (onRefresh != null) {
            PullRefreshIndicator(
                refreshing = refreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = indicatorTopPadding),
                backgroundColor = sg.linen,
                contentColor = sg.accent
            )
        }
    }
}
