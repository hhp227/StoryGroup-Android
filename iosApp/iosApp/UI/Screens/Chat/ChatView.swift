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
struct ChatView: View {
    @StateObject private var viewModel: ChatViewModel

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
            ScrollView {
                VStack(spacing: 8) {
                    if !uiState.groupRooms.isEmpty {
                        SGSectionTitle(text: "그룹 채팅")
                        ForEach(uiState.groupRooms, id: \.id) { room in
                            ChatRoomRow(
                                title: room.groupName,
                                subtitle: room.name,
                                imageUrl: nil,
                                isGroup: true
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
                        ForEach(uiState.directRooms, id: \.id) { room in
                            ChatRoomRow(
                                title: room.otherUserName,
                                subtitle: nil,
                                imageUrl: room.otherUserProfileImg,
                                isGroup: false
                            ) {
                                onOpenChatRoom(ChatRoomRef(chatRoomId: room.id, groupId: nil, title: room.otherUserName))
                            }
                        }
                    }
                }
                .padding(16)
            }
            .background(colors.paper)
        }
    }

    init(container: AppContainer, onOpenChatRoom: @escaping (ChatRoomRef) -> Void) {
        _viewModel = StateObject(wrappedValue: ChatViewModel(
            getGroupChatRoomsUseCase: container.getGroupChatRoomsUseCase,
            getDirectRoomsUseCase: container.getDirectRoomsUseCase
        ))
        self.onOpenChatRoom = onOpenChatRoom
    }
}

private struct ChatRoomRow: View {
    let title: String

    let subtitle: String?

    let imageUrl: String?

    let isGroup: Bool

    let onTap: () -> Void

    @Environment(\.sgColors) private var colors

    var body: some View {
        Button(action: onTap) {
            SGCard {
                HStack(spacing: 12) {
                    SGAvatar(
                        name: title,
                        imageUrl: imageUrl,
                        background: isGroup ? colors.accent2Soft : colors.accentSoft,
                        foreground: isGroup ? colors.accent2 : colors.accent
                    )
                    VStack(alignment: .leading, spacing: 2) {
                        Text(title).font(.subheadline.bold()).foregroundColor(colors.ink)
                        if let subtitle {
                            Text(subtitle)
                                .font(.caption)
                                .foregroundColor(colors.inkSoft)
                                .lineLimit(1)
                        }
                    }
                    Spacer()
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
        }
        .buttonStyle(.plain)
    }
}
