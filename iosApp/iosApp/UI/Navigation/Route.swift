import Foundation

/// 목적지 — composeApp ui/navigation/Route.kt와 1:1 미러.
/// NavigationStack(path:)이 요구하므로 Hashable이어야 한다.
/// ⚠️ Kotlin의 `Route.Shell`(NavHost 시작 목적지 전용)은 여기 없다 —
/// iOS는 `path` 배열이 비어 있는 상태가 곧 셸이라 별도 케이스가 필요 없다.
enum Route: Hashable {
    case groupDetail(groupId: Int64)

    /// 게시글 상세 — 라운지 글도 라운지 그룹 id로 들어오므로 홈·그룹 피드가 같은 목적지를 쓴다
    case postDetail(groupId: Int64, postId: Int64)

    /// 채팅방 — 그룹 채팅(groupId 있음)/DM(null) 공용. title은 허브가 아는 표시명
    case chatRoom(chatRoomId: Int64, groupId: Int64?, title: String)

    /// 공개 프로필 — 시트(Android는 카드 다이얼로그로 미러)
    case userProfile(userId: Int64)

    /// 게시글 작성 — groupId nil이면 라운지(홈 피드)에 게시.
    /// postId가 있으면 같은 폼이 수정 모드로 동작한다
    case createPost(groupId: Int64?, postId: Int64?)

    case groupEdit(groupId: Int64)

    /// 그룹 신고함(모더레이터)
    case groupReports(groupId: Int64)

    /// 방 통화 — DM 1:1·그룹 방 공용.
    /// ring=true는 발신(입장+벨울림), false는 수신 배너 수락으로 진입.
    /// video=false면 보이스톡(카메라 OFF·수화구 시작)
    case call(chatRoomId: Int64, title: String, ring: Bool, video: Bool)

    case accountSettings
    case appSettings
    case blockedUsers
    case createGroup
    case discoverGroups
    case search
}
