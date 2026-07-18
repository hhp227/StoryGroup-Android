import SwiftUI

/// 알림 — Compose NotificationsScreen 미러. TODO: 알림 API 연동 후 목록 구현
struct NotificationsView: View {
    let colors: SGColors

    var body: some View {
        SGEmptyState(
            title: "알림이 없습니다",
            subtitle: "새 소식이 생기면 여기에 표시됩니다.",
            colors: colors,
            systemImage: "bell.fill"
        )
    }
}
