import SwiftUI
import Shared

/// 채팅방 — 웹 MessageThread·Compose ChatRoomScreen 미러(말풍선 정렬·작성자 변경 시에만
/// 아바타/이름·첨부 렌더링, 웹처럼 시각 표기는 없음). 목록은 오래된 순으로 그리고 새 메시지가
/// 오면 맨 아래로 따라간다. 상단 근처에 닿으면 이전 페이지를 자동 로드한다(무한 스크롤 —
/// 웹엔 없는 앱 확장).
struct ChatRoomView: View {
    @StateObject private var viewModel: ChatRoomViewModel

    let title: String

    @State private var input = ""

    /// 이전 메시지 로드 앵커 — 위로 끼어드는 과거 메시지만큼 스크롤이 최상단(새 배치의 가장
    /// 오래된 쪽)으로 튀므로, 트리거 시점 맨 위 메시지 id를 기억해 두고 로드 완료 시 복원한다.
    /// SwiftUI(iOS 15)엔 Compose 키 앵커 같은 네이티브 위치 유지가 없어 보정 방식이 한계
    @State private var olderAnchorId: Int64?

    /// 초기 렌더는 최상단(가장 오래된 행)부터 그려져 자동 로드 트리거가 진입 즉시 발화한다 —
    /// 첫 하단 정렬이 끝난 다음 런루프부터 연다
    @State private var initialScrolled = false

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
                                // VM 목록은 최신순 — 화면은 뒤집어 오래된 순으로 그린다(웹 reverse 미러)
                                let ordered = Array(uiState.messages.reversed())

                                ForEach(Array(ordered.enumerated()), id: \.element.id) { index, message in
                                    MessageRow(
                                        message: message,
                                        isMine: message.userId == uiState.myUserId,
                                        showAuthor: index == 0 || ordered[index - 1].userId != message.userId
                                    )
                                    .id(message.id)
                                    // 상단 근처 행이 나타나면 이전 페이지 자동 로드(무한 스크롤) —
                                    // 재진입·소진 시 과호출은 VM이 거른다
                                    .onAppear {
                                        if initialScrolled && index < 3 {
                                            // 트리거 시점 맨 위 = 로드된 것 중 가장 오래된 메시지
                                            olderAnchorId = uiState.messages.last?.id
                                            viewModel.onAction(.loadOlder)
                                        }
                                    }
                                }
                            }
                            .padding(.horizontal, 16)
                            .padding(.vertical, 8)
                        }
                        // 이전 페이지 로딩 표시 — 행으로 넣으면 등장/소멸만큼 목록이 밀린다(오버레이 고정)
                        .overlay(alignment: .top) {
                            if uiState.isLoadingOlder {
                                ProgressView().padding(.top, 8)
                            }
                        }
                        .onAppear {
                            if let latest = uiState.messages.first?.id {
                                proxy.scrollTo(latest, anchor: .bottom)
                            }
                            DispatchQueue.main.async { initialScrolled = true }
                        }
                        // 새 메시지 도착/전송 시 맨 아래로 따라간다(웹 auto-scroll 미러)
                        .onChange(of: uiState.messages.first?.id) { latest in
                            if let latest {
                                withAnimation { proxy.scrollTo(latest, anchor: .bottom) }
                            }
                        }
                        // 이전 메시지 로드 완료 시 앵커 메시지를 상단에 다시 붙인다 —
                        // 새로 끼워진 행 레이아웃이 잡힌 다음 런루프에 스크롤해야 위치가 정확하다
                        .onChange(of: uiState.isLoadingOlder) { isLoadingOlder in
                            guard !isLoadingOlder, let anchorId = olderAnchorId else { return }
                            olderAnchorId = nil
                            DispatchQueue.main.async {
                                proxy.scrollTo(anchorId, anchor: .top)
                            }
                        }
                        // 키보드가 올라와 리스트가 줄어들 때 최신 메시지가 가려지지 않게 따라간다.
                        // willShow 즉시는 SwiftUI 키보드 인셋 반영 전이라 스크롤이 짧게 끝난다 —
                        // 한 런루프 미뤄 인셋 커밋 후, 키보드와 같은 시간으로 애니메이션하면
                        // 리스트가 키보드와 동시에 밀려 올라간다. didShow는 빗나갔을 때의 보정용
                        .onReceive(
                            NotificationCenter.default.publisher(for: UIResponder.keyboardWillShowNotification)
                        ) { notification in
                            let duration = notification
                                .userInfo?[UIResponder.keyboardAnimationDurationUserInfoKey] as? Double

                            DispatchQueue.main.async {
                                scrollToLatest(proxy, duration: duration ?? 0.25)
                            }
                        }
                        .onReceive(
                            NotificationCenter.default.publisher(for: UIResponder.keyboardDidShowNotification)
                        ) { _ in
                            scrollToLatest(proxy)
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

    private func scrollToLatest(_ proxy: ScrollViewProxy, duration: Double? = nil) {
        if let latest = viewModel.uiState.messages.first?.id {
            withAnimation(duration.map { Animation.easeOut(duration: $0) } ?? .default) {
                proxy.scrollTo(latest, anchor: .bottom)
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
        // 아바타는 상단 정렬 — Compose Row 기본값(Top) 미러
        HStack(alignment: .top, spacing: 8) {
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
