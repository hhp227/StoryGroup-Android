import Foundation

/// 화면 간 결과 신호 — composeApp ui/navigation/NavResult.kt 미러.
/// ⚠️ 이벤트가 아니라 상태로 다뤄야 한다: 결과를 낸 화면이 떠 있는 동안 받을 화면은
/// NavigationStack 백스택에만 있고 뷰 트리에서 빠져 있어 이벤트를 놓친다.
/// `pendingResults`가 Set이므로 Hashable이어야 한다.
enum NavResult: Hashable {
    /// groupId == nil 이면 라운지(홈 피드)에 올린 글
    case postCreated(groupId: Int64?)

    case postUpdated(groupId: Int64, postId: Int64)

    case groupUpdated(groupId: Int64)

    /// 나가기·삭제·수정으로 내 그룹 목록이 바뀜
    case groupsChanged
}
