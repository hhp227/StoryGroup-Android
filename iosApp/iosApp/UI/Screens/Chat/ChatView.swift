import SwiftUI

/// 채팅 허브 API(GET /api/chat-rooms, 라운지 제외) 연동 전 표시용 모델 — Compose ChatRoomUiModel 미러
struct ChatRoomUiModel: Identifiable {
    let id: Int
    let name: String
    let lastMessage: String
    let lastMessageAt: String
    let unreadCount: Int
    let isGroup: Bool
}

private let sampleRooms = [
    ChatRoomUiModel(id: 1, name: "등산 모임", lastMessage: "이번 주말 코스 공유합니다", lastMessageAt: "오후 2:41", unreadCount: 3, isGroup: true),
    ChatRoomUiModel(id: 2, name: "스터디 그룹", lastMessage: "다음 발표 자료 올렸어요", lastMessageAt: "오전 11:02", unreadCount: 0, isGroup: true),
    ChatRoomUiModel(id: 3, name: "김재환", lastMessage: "사진 고마워요!", lastMessageAt: "어제", unreadCount: 1, isGroup: false),
    ChatRoomUiModel(id: 4, name: "이수진", lastMessage: "네 내일 봬요", lastMessageAt: "월요일", unreadCount: 0, isGroup: false)
]

/// 채팅 허브 — 웹 /dm·Compose ChatScreen 미러(그룹 채팅 + 다이렉트 메시지, 라운지 제외)
struct ChatView: View {
    let colors: SGColors

    private var groupRooms: [ChatRoomUiModel] { sampleRooms.filter { $0.isGroup } }
    private var directRooms: [ChatRoomUiModel] { sampleRooms.filter { !$0.isGroup } }

    var body: some View {
        ScrollView {
            VStack(spacing: 8) {
                SGSectionTitle(text: "그룹 채팅", colors: colors)
                ForEach(groupRooms) { room in
                    ChatRoomRow(room: room, colors: colors)
                }
                Spacer().frame(height: 12)
                SGSectionTitle(text: "다이렉트 메시지", colors: colors)
                ForEach(directRooms) { room in
                    ChatRoomRow(room: room, colors: colors)
                }
            }
            .padding(16)
        }
        .background(colors.paper)
    }
}

private struct ChatRoomRow: View {
    let room: ChatRoomUiModel

    let colors: SGColors

    var body: some View {
        SGCard(colors: colors) {
            HStack(spacing: 12) {
                SGAvatar(
                    name: room.name,
                    colors: colors,
                    background: room.isGroup ? colors.accent2Soft : colors.accentSoft,
                    foreground: room.isGroup ? colors.accent2 : colors.accent
                )
                VStack(alignment: .leading, spacing: 2) {
                    Text(room.name).font(.subheadline.bold()).foregroundColor(colors.ink)
                    Text(room.lastMessage)
                        .font(.caption)
                        .foregroundColor(colors.inkSoft)
                        .lineLimit(1)
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 2) {
                    Text(room.lastMessageAt).font(.caption).foregroundColor(colors.inkFaint)
                    if room.unreadCount > 0 {
                        Text("\(room.unreadCount)")
                            .font(.caption2.bold())
                            .foregroundColor(colors.accent)
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
    }
}
