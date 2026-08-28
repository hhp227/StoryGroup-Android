import Foundation

/// 푸시 탭 → 세션 셸 라우팅 브리지(composeApp PushDeepLink 미러).
/// AppDelegate(딜리버리)와 MainShellView(소비)가 라이프사이클이 달라 전역 싱글턴으로 중계한다.
/// 콜드 스타트(앱 종료 상태에서 알림 탭)면 셸이 아직 없는 시점에 값이 들어오는데,
/// @Published는 새 구독자에게 현재 값을 즉시 흘려보내므로 셸이 뜨는 순간 그대로 소비된다.
final class PendingPushRoute: ObservableObject {
    static let shared = PendingPushRoute()

    @Published var route: Route?

    /// 라우팅 정보가 없을 때의 폴백 — 알림 탭 전환 신호(설계 §9)
    @Published var openNotifications = false

    /// APNs userInfo(서버 data 키 사전, 값은 전부 문자열) → Route.
    /// 키 사전은 웹 SW(Task 14)·Android(Task 12)와 동일: kind/chatRoomId/groupId/postId/roomTitle
    func set(from userInfo: [AnyHashable: Any]) {
        let kind = userInfo["kind"] as? String ?? "NOTIFICATION"
        let chatRoomId = (userInfo["chatRoomId"] as? String).flatMap(Int64.init)
        let groupId = (userInfo["groupId"] as? String).flatMap(Int64.init)
        let postId = (userInfo["postId"] as? String).flatMap(Int64.init)
        let roomTitle = userInfo["roomTitle"] as? String

        // @Published는 메인 스레드에서만 갱신한다(UNUserNotificationCenter 델리게이트는 메인 보장이 없다)
        DispatchQueue.main.async {
            if kind == "CHAT", let chatRoomId = chatRoomId {
                // DM이면 groupId가 없다(Route.chatRoom의 groupId는 옵셔널) — 제목은 서버가 실어 보낸 방 이름
                self.route = .chatRoom(chatRoomId: chatRoomId, groupId: groupId, title: roomTitle ?? "")
            } else if let groupId = groupId, let postId = postId {
                self.route = .postDetail(groupId: groupId, postId: postId)
            } else if let groupId = groupId {
                self.route = .groupDetail(groupId: groupId)
            } else {
                // 알림 목록 폴백(설계 §9) — 탭 전환은 MainShellView가 처리한다
                self.openNotifications = true
            }
        }
    }
}
