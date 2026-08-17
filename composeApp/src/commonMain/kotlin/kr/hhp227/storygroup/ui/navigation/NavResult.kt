package kr.hhp227.storygroup.ui.navigation

/**
 * 화면 간 결과 신호 — 기존 savedStateHandle 키(POST_CREATED_KEY·GROUP_UPDATED_KEY)와
 * 셸 플래그(homeRefreshPending·groupsRefreshPending)를 대체한다.
 * ⚠️ 이벤트가 아니라 상태로 다뤄야 한다: 결과를 낸 화면이 떠 있는 동안 받을 화면은
 * NavHost 백스택에만 있고 컴포지션에서 빠져 있어 이벤트를 놓친다.
 */
sealed interface NavResult {

    /** groupId == null 이면 라운지(홈 피드)에 올린 글 */
    data class PostCreated(val groupId: Long?) : NavResult

    data class PostUpdated(val groupId: Long, val postId: Long) : NavResult

    data class GroupUpdated(val groupId: Long) : NavResult

    /** 나가기·삭제·수정으로 내 그룹 목록이 바뀜 */
    data object GroupsChanged : NavResult
}
