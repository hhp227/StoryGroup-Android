package kr.hhp227.storygroup.ui.navigation

/**
 * VM → 호스트 이동 지시. 둘이면 충분하다 —
 * 다른 방으로 갈 때 프로필 다이얼로그는 Navigation이 알아서 pop한다.
 * 탭은 이벤트가 아니라 UiState.currentTab이다.
 */
sealed interface NavigationEvent {
    data class NavigateTo(val route: Route) : NavigationEvent
    data object NavigateBack : NavigationEvent
}
