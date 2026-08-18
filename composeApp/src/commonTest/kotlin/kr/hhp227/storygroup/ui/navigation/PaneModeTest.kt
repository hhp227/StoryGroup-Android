package kr.hhp227.storygroup.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class PaneModeTest {

    /** 목록↔상세 성격만 우측 패널 — 좌우 문맥이 실제로 이어지는 목적지들 */
    @Test
    fun detailRoutesUsePane() {
        assertEquals(PaneMode.DETAIL_PANE, paneModeFor(Route.GroupDetail(1L)))
        assertEquals(PaneMode.DETAIL_PANE, paneModeFor(Route.PostDetail(1L, 2L)))
        assertEquals(PaneMode.DETAIL_PANE, paneModeFor(Route.ChatRoom(1L, null, "방")))
    }

    /**
     * 공개 프로필은 카드 다이얼로그로 남는다 — 스크림 너머로 뒤 화면이 보이고,
     * 같은 방 가드가 백스택 바로 아래 엔트리에 묶여 있어 패널로 바꾸면 깨진다
     */
    @Test
    fun userProfileStaysDialog() {
        assertEquals(PaneMode.DIALOG, paneModeFor(Route.UserProfile(7L)))
    }

    /** 작업에 몰입하는 화면은 넓은 창에서도 전체를 덮는다 */
    @Test
    fun taskRoutesUseFullScreen() {
        val fullScreenRoutes = listOf(
            Route.Shell,
            Route.CreatePost(null),
            Route.GroupEdit(1L),
            Route.GroupReports(1L),
            Route.Call(1L, "방", ring = true),
            Route.AccountSettings,
            Route.AppSettings,
            Route.BlockedUsers,
            Route.CreateGroup,
            Route.DiscoverGroups,
            Route.Search
        )

        fullScreenRoutes.forEach { route ->
            assertEquals(PaneMode.FULL_SCREEN, paneModeFor(route), "route=$route")
        }
    }
}
