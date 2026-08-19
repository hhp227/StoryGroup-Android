import SwiftUI
import Shared

/// 채팅방 push 대상 — Compose ChatRoomRoute(chatRoomId, groupId, title) 미러.
/// 그룹 방은 groupId가 있고(그룹 경로 REST), DM은 nil(/api/dm 경로).
struct ChatRoomRef: Equatable {
    let chatRoomId: Int64
    let groupId: Int64?
    let title: String
}

/// 채팅 허브 — 웹 /dm·Compose ChatScreen 미러(그룹 채팅 + 다이렉트 메시지, 라운지 제외).
/// 행 탭 시 채팅방 풀스크린 push — 그룹 방은 그룹명, DM은 상대 이름이 제목이 된다.
/// VM은 셸(MainShellView)이 소유·주입한다 — 채팅 탭 뱃지와 같은 인스턴스(Compose sessionChatViewModel 미러)
struct ChatView: View {
    @ObservedObject var viewModel: ChatViewModel

    /// 채팅방 풀스크린 push — MainShellView(루트 NavigationStack)로 위임
    let onOpenChatRoom: (ChatRoomRef) -> Void

    @Environment(\.sgColors) private var colors

    var body: some View {
        let uiState = viewModel.uiState

        if uiState.groupRooms.isEmpty && uiState.directRooms.isEmpty && uiState.isLoading {
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(colors.paper)
        } else if uiState.groupRooms.isEmpty && uiState.directRooms.isEmpty, let error = uiState.error {
            VStack(spacing: 8) {
                Text(error).font(.subheadline).foregroundColor(colors.rust)
                Button("다시 시도") { viewModel.onAction(.refresh) }
                    .foregroundColor(colors.accent)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(colors.paper)
        } else if uiState.groupRooms.isEmpty && uiState.directRooms.isEmpty {
            SGEmptyState(
                title: "채팅방이 없습니다",
                subtitle: "그룹에 가입하거나 친구에게 메시지를 보내보세요.",
                systemImage: "bubble.left"
            )
            .background(colors.paper)
        } else {
            // 카카오톡식 풀블리드 행 — 카드 없이 행이 자체 패딩을 갖고, 섹션 제목만 좌우 여백을 준다
            ScrollView {
                VStack(spacing: 0) {
                    if !uiState.groupRooms.isEmpty {
                        SGSectionTitle(text: "그룹 채팅")
                            .padding(.horizontal, 16)
                            .padding(.bottom, 4)
                        ForEach(uiState.groupRooms, id: \.id) { room in
                            ChatRoomRow(
                                title: room.groupName,
                                roomName: room.name,
                                imageUrl: nil,
                                isGroup: true,
                                unreadCount: room.unreadCount,
                                lastMessageText: room.lastMessageText,
                                lastMessageType: room.lastMessageType,
                                lastMessageAt: room.lastMessageAt
                            ) {
                                onOpenChatRoom(ChatRoomRef(chatRoomId: room.id, groupId: room.groupId, title: room.groupName))
                            }
                        }
                    }
                    if !uiState.directRooms.isEmpty {
                        if !uiState.groupRooms.isEmpty {
                            Spacer().frame(height: 12)
                        }
                        SGSectionTitle(text: "다이렉트 메시지")
                            .padding(.horizontal, 16)
                            .padding(.bottom, 4)
                        ForEach(uiState.directRooms, id: \.id) { room in
                            ChatRoomRow(
                                title: room.otherUserName,
                                roomName: nil,
                                imageUrl: room.otherUserProfileImg,
                                isGroup: false,
                                unreadCount: room.unreadCount,
                                lastMessageText: room.lastMessageText,
                                lastMessageType: room.lastMessageType,
                                lastMessageAt: room.lastMessageAt
                            ) {
                                onOpenChatRoom(ChatRoomRef(chatRoomId: room.id, groupId: nil, title: room.otherUserName))
                            }
                        }
                    }
                }
                .padding(.vertical, 8)
            }
            .background(colors.paper)
        }
    }

    init(viewModel: ChatViewModel, onOpenChatRoom: @escaping (ChatRoomRef) -> Void) {
        self.viewModel = viewModel
        self.onOpenChatRoom = onOpenChatRoom
    }
}

/// 채팅방 한 줄 — 카카오톡식(아바타 + 제목·마지막 메시지 2줄 + 우측 시각·미읽음 버블), 카드 없음.
/// Compose ChatRoomRow와 1:1 미러
private struct ChatRoomRow: View {
    let title: String

    let roomName: String?

    let imageUrl: String?

    let isGroup: Bool

    let unreadCount: Int64

    let lastMessageText: String?

    let lastMessageType: String?

    let lastMessageAt: String?

    let onTap: () -> Void

    @Environment(\.sgColors) private var colors

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                SGAvatar(
                    name: title,
                    size: 52,
                    imageUrl: imageUrl,
                    background: isGroup ? colors.accent2Soft : colors.accentSoft,
                    foreground: isGroup ? colors.accent2 : colors.accent
                )
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 4) {
                        Text(title).font(.subheadline.bold()).foregroundColor(colors.ink).lineLimit(1)
                        if let roomName {
                            Text(roomName)
                                .font(.caption)
                                .foregroundColor(colors.inkSoft)
                                .lineLimit(1)
                        }
                    }
                    Text(preview)
                        .font(.caption)
                        .foregroundColor(colors.inkSoft)
                        .lineLimit(1)
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 4) {
                    if let lastMessageAt {
                        Text(TimeFormats.relative(lastMessageAt))
                            .font(.caption2)
                            .foregroundColor(colors.inkSoft)
                    }
                    SGUnreadBadge(count: unreadCount)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    /// 미리보기 라벨 — 첨부 전용 메시지(text 빈 문자열)는 종류로 표기한다. Compose messagePreview와 1:1 미러
    private var preview: String {
        if lastMessageAt == nil { return "아직 메시지가 없습니다" }
        if let lastMessageText, !lastMessageText.isEmpty { return lastMessageText }
        if lastMessageType?.hasPrefix("image/") == true { return "사진" }
        return "파일"
    }
}
