package kr.hhp227.storygroup.ui.navigation

/**
 * 화면이 올리는 의도 — 화면은 Route를 몰라도 된다(ConCafe 패턴).
 * iosApp UI/Navigation/NavigationAction.swift와 1:1 미러.
 */
sealed interface NavigationAction {

    data class NavigateToGroupDetail(val groupId: Long) : NavigationAction

    data class NavigateToPostDetail(val groupId: Long, val postId: Long) : NavigationAction

    data class NavigateToChatRoom(
        val chatRoomId: Long,
        val groupId: Long?,
        val title: String
    ) : NavigationAction

    data class NavigateToUserProfile(val userId: Long) : NavigationAction

    data class NavigateToCreatePost(val groupId: Long?, val postId: Long? = null) : NavigationAction

    data class NavigateToGroupEdit(val groupId: Long) : NavigationAction

    data class NavigateToGroupReports(val groupId: Long) : NavigationAction

    /** 발신 — 입장+벨울림(그룹 방은 서버가 방 멤버 전원 팬아웃) */
    data class StartCall(val chatRoomId: Long, val title: String, val video: Boolean) : NavigationAction

    /** 수신 배너 수락 — 입장만(구독=입장), 벨은 다시 울리지 않는다 */
    data class AcceptIncomingCall(
        val chatRoomId: Long,
        val title: String,
        val video: Boolean
    ) : NavigationAction

    data object NavigateToAccountSettings : NavigationAction

    data object NavigateToAppSettings : NavigationAction

    data object NavigateToBlockedUsers : NavigationAction

    data object NavigateToCreateGroup : NavigationAction

    data object NavigateToDiscoverGroups : NavigationAction

    data object NavigateToSearch : NavigationAction

    data object NavigateBack : NavigationAction

    data class SelectTab(val destination: MainDestination) : NavigationAction

    /**
     * 프로필 다이얼로그의 DM 버튼 — 바로 아래가 같은 방이면 방을 또 쌓지 않고 닫기만 한다
     * (카카오톡 방식. DM 열기는 멱등이라 id 비교가 정확하다)
     */
    data class OpenChatRoomFromProfile(
        val chatRoomId: Long,
        val groupId: Long?,
        val title: String,
        val underlyingChatRoomId: Long?
    ) : NavigationAction

    data class PublishResult(val result: NavResult) : NavigationAction

    data class ConsumeResult(val result: NavResult) : NavigationAction
}
