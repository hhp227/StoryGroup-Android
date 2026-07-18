import SwiftUI

/// 친구 API 연동 전 표시용 모델 — Compose FriendUiModel 미러
struct FriendUiModel: Identifiable {
    let id: Int
    let name: String
    let email: String
}

private let sampleFriends = [
    FriendUiModel(id: 1, name: "김재환", email: "jaehwan@example.com"),
    FriendUiModel(id: 2, name: "이수진", email: "sujin@example.com"),
    FriendUiModel(id: 3, name: "박민준", email: "minjun@example.com")
]

/// 친구 목록 — 웹 /search(친구 기본 화면)·Compose FriendsScreen 미러
struct FriendsView: View {
    let colors: SGColors

    var body: some View {
        ScrollView {
            VStack(spacing: 8) {
                ForEach(sampleFriends) { friend in
                    FriendRow(friend: friend, colors: colors)
                }
            }
            .padding(16)
        }
        .background(colors.paper)
    }
}

private struct FriendRow: View {
    let friend: FriendUiModel

    let colors: SGColors

    var body: some View {
        SGCard(colors: colors) {
            HStack(spacing: 12) {
                SGAvatar(name: friend.name, colors: colors)
                VStack(alignment: .leading, spacing: 2) {
                    Text(friend.name).font(.subheadline.bold()).foregroundColor(colors.ink)
                    Text(friend.email).font(.caption).foregroundColor(colors.inkFaint)
                }
                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
    }
}
