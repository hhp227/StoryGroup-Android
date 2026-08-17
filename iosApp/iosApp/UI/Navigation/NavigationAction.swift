import Foundation

/// 화면이 올리는 의도 — 화면은 Route를 몰라도 된다(ConCafe 패턴).
/// composeApp ui/navigation/NavigationAction.kt 미러.
enum NavigationAction {
    case navigateToGroupDetail(groupId: Int64)
    case navigateToPostDetail(groupId: Int64, postId: Int64)
    case navigateToChatRoom(chatRoomId: Int64, groupId: Int64?, title: String)
    case navigateToUserProfile(userId: Int64)
    case navigateToCreatePost(groupId: Int64?, postId: Int64?)
    case navigateToGroupEdit(groupId: Int64)
    case navigateToGroupReports(groupId: Int64)

    /// 발신 — 입장+벨울림(그룹 방은 서버가 방 멤버 전원 팬아웃)
    case startCall(chatRoomId: Int64, title: String, video: Bool)

    /// 수신 배너 수락 — 입장만(구독=입장), 벨은 다시 울리지 않는다
    case acceptIncomingCall(chatRoomId: Int64, title: String, video: Bool)

    case navigateToAccountSettings
    case navigateToAppSettings
    case navigateToBlockedUsers
    case navigateToCreateGroup
    case navigateToDiscoverGroups
    case navigateToSearch
    case navigateBack

    /// SGDestination은 UI/Shell/MainShellView.swift에 있는 탭 enum이다(파일 이동은 하지 않음)
    case selectTab(destination: SGDestination)

    /// 프로필 시트의 DM 버튼 — 바로 아래가 같은 방이면 방을 또 쌓지 않고 닫기만 한다
    /// (카카오톡 방식. DM 열기는 멱등이라 id 비교가 정확하다)
    case openChatRoomFromProfile(chatRoomId: Int64, groupId: Int64?, title: String, underlyingChatRoomId: Int64?)

    case publishResult(NavResult)
    case consumeResult(NavResult)
}
