import SwiftUI
import Shared

/// 채팅방 — 웹 MessageThread·Compose ChatRoomScreen 미러(말풍선 정렬·작성자 변경 시에만
/// 아바타/이름·첨부 렌더링, 웹처럼 시각 표기는 없음). 목록은 오래된 순으로 그리고 새 메시지가
/// 오면 맨 아래로 따라간다. "이전 메시지 보기" 버튼(웹엔 없는 앱 확장)이 맨 위에 놓인다.
struct ChatRoomView: View {
    @StateObject private var viewModel: ChatRoomViewModel

    let title: String

    @State private var input = ""

    @Environment(\.sgColors) private var colors

    var body: some View {
        let uiState = viewModel.uiState

        VStack(spacing: 0) {
            ScrollViewReader { proxy in
                Group {
                    if uiState.messages.isEmpty && uiState.isLoading {
                        ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
                    } else if uiState.messages.isEmpty, let error = uiState.error {
                        VStack(spacing: 8) {
                            Text(error).font(.subheadline).foregroundColor(colors.rust)
                            Button("다시 시도") { viewModel.onAction(.refresh) }
                                .foregroundColor(colors.accent)
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                    } else if uiState.messages.isEmpty {
                        SGEmptyState(
                            title: "메시지가 없습니다",
                            subtitle: "첫 메시지를 보내보세요.",
                            systemImage: "bubble.left"
                        )
                    } else {
                        ScrollView {
                            LazyVStack(spacing: 4) {
                                if uiState.canLoadOlder || uiState.isLoadingOlder {
                                    if uiState.isLoadingOlder {
                                        ProgressView().padding(.vertical, 8)
                                    } else {
                                        Button("이전 메시지 보기") { viewModel.onAction(.loadOlder) }
                                            .font(.subheadline)
                                            .foregroundColor(colors.accent)
                                            .padding(.vertical, 8)
                                    }
                                }
                                // VM 목록은 최신순 — 화면은 뒤집어 오래된 순으로 그린다(웹 reverse 미러)
                                let ordered = Array(uiState.messages.reversed())

                                ForEach(Array(ordered.enumerated()), id: \.element.id) { index, message in
                                    MessageRow(
                                        message: message,
                                        isMine: message.userId == uiState.myUserId,
                                        showAuthor: index == 0 || ordered[index - 1].userId != message.userId
                                    )
                                    .id(message.id)
                                }
                            }
                            .padding(.horizontal, 16)
                            .padding(.vertical, 8)
                        }
                        .onAppear {
                            if let latest = uiState.messages.first?.id {
                                proxy.scrollTo(latest, anchor: .bottom)
                            }
                        }
                        // 새 메시지 도착/전송 시 맨 아래로 따라간다(웹 auto-scroll 미러)
                        .onChange(of: uiState.messages.first?.id) { latest in
                            if let latest {
                                withAnimation { proxy.scrollTo(latest, anchor: .bottom) }
                            }
                        }
                        // 키보드가 올라와 리스트가 줄어들 때 최신 메시지가 가려지지 않게 따라간다
                        .onReceive(
                            NotificationCenter.default.publisher(for: UIResponder.keyboardWillShowNotification)
                        ) { _ in
                            if let latest = uiState.messages.first?.id {
                                withAnimation { proxy.scrollTo(latest, anchor: .bottom) }
                            }
                        }
                    }
                }
            }
            if let actionError = uiState.actionError {
                Text(actionError)
                    .font(.caption)
                    .foregroundColor(colors.rust)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 4)
            }
            inputBar(isSending: uiState.isSending)
        }
        .background(colors.paper.ignoresSafeArea())
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .onReceive(viewModel.event) { event in
            switch event {
            case .sent: input = ""
            }
        }
    }

    private func inputBar(isSending: Bool) -> some View {
        HStack(spacing: 8) {
            SGTextField(label: "메시지 입력", text: $input)
            Button(action: { viewModel.onAction(.send(text: input)) }) {
                if isSending {
                    ProgressView()
                } else {
                    Image(systemName: "arrow.up.circle.fill")
                        .font(.system(size: 28))
                        .foregroundColor(
                            input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                                ? colors.inkFaint
                                : colors.accent
                        )
                }
            }
            .disabled(input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isSending)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 6)
        .background(colors.linen)
    }

    init(chatRoomId: Int64, groupId: Int64?, title: String, container: AppContainer) {
        _viewModel = StateObject(wrappedValue: ChatRoomViewModel(
            groupId: groupId,
            chatRoomId: chatRoomId,
            getChatMessagesUseCase: container.getChatMessagesUseCase,
            sendChatMessageUseCase: container.sendChatMessageUseCase,
            markChatMessagesReadUseCase: container.markChatMessagesReadUseCase,
            observeChatRoomEventsUseCase: container.observeChatRoomEventsUseCase,
            getCurrentUserIdUseCase: container.getCurrentUserIdUseCase
        ))
        self.title = title
    }
}

/// 웹 MessageBubble 미러 — 내 메시지는 우측 accent, 타인은 좌측 linen+작성자 변경 시 아바타/이름
private struct MessageRow: View {
    let message: ChatMessage

    let isMine: Bool

    let showAuthor: Bool

    @Environment(\.sgColors) private var colors

    var body: some View {
        HStack(alignment: .bottom, spacing: 8) {
            if isMine {
                Spacer(minLength: 48)
            } else {
                if showAuthor {
                    SGAvatar(name: message.authorName, size: 32, imageUrl: message.authorProfileImg)
                } else {
                    // 같은 작성자 연속 메시지는 아바타 없이 자리만 맞춘다(웹 spacer 미러)
                    Spacer().frame(width: 32)
                }
            }
            VStack(alignment: isMine ? .trailing : .leading, spacing: 2) {
                if !isMine && showAuthor {
                    Text(message.authorName)
                        .font(.caption.bold())
                        .foregroundColor(colors.inkSoft)
                }
                if let attachment = message.attachment {
                    attachmentContent(attachment)
                }
                if !message.text.isEmpty {
                    Text(message.text)
                        .font(.subheadline)
                        .foregroundColor(isMine ? colors.onAccent : colors.ink)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(
                            RoundedRectangle(cornerRadius: 16)
                                .fill(isMine ? colors.accent : colors.linen)
                        )
                }
            }
            if !isMine {
                Spacer(minLength: 48)
            }
        }
        .frame(maxWidth: .infinity, alignment: isMine ? .trailing : .leading)
    }

    @ViewBuilder private func attachmentContent(_ attachment: ChatAttachment) -> some View {
        if attachment.isImage {
            // 이미지 첨부는 말풍선 배경 없이 그린다(웹 미러)
            AsyncImage(url: URL(string: attachment.url)) { phase in
                if let image = phase.image {
                    image.resizable().scaledToFill()
                } else {
                    colors.linen
                }
            }
            .frame(width: 200, height: 200)
            .clipShape(RoundedRectangle(cornerRadius: 12))
        } else {
            HStack(spacing: 6) {
                Image(systemName: "doc")
                    .font(.system(size: 14))
                    .foregroundColor(colors.inkSoft)
                Text(attachment.name ?? "파일")
                    .font(.caption)
                    .foregroundColor(colors.ink)
                    .lineLimit(1)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(colors.linen)
            )
        }
    }
}
