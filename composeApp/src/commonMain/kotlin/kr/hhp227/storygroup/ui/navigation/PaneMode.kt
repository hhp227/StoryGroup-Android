package kr.hhp227.storygroup.ui.navigation

/**
 * 목적지가 화면을 어떻게 차지하는가 — Desktop 2-pane 판정에 쓴다.
 * 순수 함수라 3플랫폼이 같은 규칙을 공유하고 단위 테스트가 가능하다.
 */
enum class PaneMode {
    /** 넓은 창(≥900dp)에서 좌측 셸과 나란히 우측 패널 */
    DETAIL_PANE,

    /** 창 폭과 무관하게 전체를 덮는다 */
    FULL_SCREEN,

    /** 스크림 위 카드 — 뒤 화면이 남는다 */
    DIALOG
}

/**
 * 목록↔상세로 문맥이 이어지는 목적지만 패널이다.
 * 작성·설정·통화처럼 작업에 몰입하는 화면은 넓은 창에서도 전체를 덮는 편이 낫다.
 */
fun paneModeFor(route: Route): PaneMode = when (route) {
    is Route.GroupDetail, is Route.PostDetail, is Route.ChatRoom -> PaneMode.DETAIL_PANE
    is Route.UserProfile -> PaneMode.DIALOG
    Route.Shell,
    is Route.CreatePost,
    is Route.GroupEdit,
    is Route.GroupReports,
    is Route.Call,
    Route.AccountSettings,
    Route.AppSettings,
    Route.BlockedUsers,
    Route.CreateGroup,
    Route.DiscoverGroups,
    Route.Search -> PaneMode.FULL_SCREEN
}
