import Combine
import Foundation
import Shared

/// 채팅방 — composeApp ChatRoomViewModel.kt와 1:1 미러.
/// 이력은 REST 최신순 오프셋 페이징, 실시간 수신은 STOMP 구독(VM 수명 = 소켓 수명),
/// 전송·읽음 보고는 REST(웹 미러). 메시지 목록은 서버 응답 그대로 최신순으로 들고
/// 화면이 뒤집어 그린다. 재연결(connected)·연결 유실(disconnected) 시 최신 페이지를
/// 다시 읽어 끊김 공백을 메꾼다 — 유실 시 REST 재조회는 만료 토큰 리프레시 역할을 겸한다.
final class ChatRoomViewModel: MviViewModel {
    @Published private(set) var uiState: UiState

    let event = PassthroughSubject<Event, Never>()

    private var cancellables = Set<AnyCancellable>()

    private let chatRoomId: Int64

    private let groupId: Int64?

    private let getChatMessagesUseCase: GetChatMessagesUseCase

    private let sendChatMessageUseCase: SendChatMessageUseCase

    private let markChatMessagesReadUseCase: MarkChatMessagesReadUseCase

    /// 최신순 페이징 커서 — 재조회(loadLatest)마다 0으로 되돌아간다(웹 전체 교체 미러)
    private var oldestLoadedPage: Int32 = 0

    /// 첫 connected는 init 로드와 겹치므로 재연결부터 공백 메꿈 재조회를 한다
    private var hasConnectedOnce = false

    /// Kotlin `groupId: Long?` 파라미터 대응 — nil이면 DM 경로(/api/dm)를 탄다
    private var kotlinGroupId: KotlinLong? { groupId.map { KotlinLong(value: $0) } }

    func onAction(_ action: Action) {
        switch action {
        case .refresh: loadLatest()
        case .loadOlder: loadOlder()
        case .send(let text): send(text: text)
        }
    }

    /// 최신 페이지로 전체 교체 — 진입/재시도/재연결 공용(웹 refresh-on-reconnect 미러)
    private func loadLatest() {
        if uiState.isLoading { return }

        uiState.isLoading = true
        uiState.error = nil
        Task { @MainActor in
            do {
                let fetched = try await getChatMessagesUseCase.invoke(
                    groupId: kotlinGroupId,
                    chatRoomId: chatRoomId,
                    page: 0,
                    size: Self.pageSize
                )
                oldestLoadedPage = 0
                uiState.isLoading = false
                uiState.messages = fetched
                uiState.canLoadOlder = fetched.count == Int(Self.pageSize)
                // 최신순 첫 항목 = 가장 최근 메시지
                if let latest = fetched.first { reportRead(lastReadMessageId: latest.id) }
            } catch {
                uiState.isLoading = false
                uiState.error = error.kotlinMessage(fallback: "메시지를 불러오지 못했습니다.")
            }
        }
    }

    private func loadOlder() {
        if uiState.isLoading || uiState.isLoadingOlder || !uiState.canLoadOlder { return }

        uiState.isLoadingOlder = true
        uiState.actionError = nil
        Task { @MainActor in
            let page = oldestLoadedPage + 1

            do {
                let fetched = try await getChatMessagesUseCase.invoke(
                    groupId: kotlinGroupId,
                    chatRoomId: chatRoomId,
                    page: page,
                    size: Self.pageSize
                )
                oldestLoadedPage = page
                let knownIds = Set(uiState.messages.map { $0.id })
                uiState.isLoadingOlder = false
                // 로드 사이 새 메시지 유입으로 오프셋이 밀리면 중복이 올 수 있어 id로 거른다
                uiState.messages += fetched.filter { !knownIds.contains($0.id) }
                uiState.canLoadOlder = fetched.count == Int(Self.pageSize)
            } catch {
                uiState.isLoadingOlder = false
                uiState.actionError = error.kotlinMessage(fallback: "이전 메시지를 불러오지 못했습니다.")
            }
        }
    }

    private func send(text: String) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)

        if trimmed.isEmpty || uiState.isSending { return }

        uiState.isSending = true
        uiState.actionError = nil
        Task { @MainActor in
            do {
                let message = try await sendChatMessageUseCase.invoke(
                    groupId: kotlinGroupId,
                    chatRoomId: chatRoomId,
                    text: trimmed
                )
                uiState.isSending = false
                // STOMP 브로드캐스트가 먼저 도착했을 수 있어 id 중복 제거를 거친다(웹 미러)
                appendMessage(message)
                event.send(.sent)
            } catch {
                uiState.isSending = false
                uiState.actionError = error.kotlinMessage(fallback: "전송에 실패했습니다.")
            }
        }
    }

    private func handleEvent(_ chatEvent: ChatEvent) {
        switch chatEvent.type {
        case .connected:
            if hasConnectedOnce { loadLatest() } else { hasConnectedOnce = true }
        case .disconnected:
            loadLatest()
        case .messageCreated:
            if let message = chatEvent.message { appendMessage(message) }
        case .messageUpdated:
            if let updated = chatEvent.message {
                uiState.messages = uiState.messages.map { $0.id == updated.id ? updated : $0 }
            }
        case .messageDeleted:
            if let deletedId = chatEvent.messageId?.int64Value {
                uiState.messages.removeAll { $0.id == deletedId }
            }
        // 타이핑/프레즌스/읽음 수 표시는 후속
        default:
            break
        }
    }

    private func appendMessage(_ message: ChatMessage) {
        if uiState.messages.contains(where: { $0.id == message.id }) { return }

        uiState.messages.insert(message, at: 0)
        reportRead(lastReadMessageId: message.id)
    }

    /// 읽음 보고는 파이어-앤-포겟 — 실패해도 다음 보고가 따라잡는다(서버 GREATEST 단조 보장)
    private func reportRead(lastReadMessageId: Int64) {
        Task { @MainActor in
            try? await markChatMessagesReadUseCase.invoke(
                groupId: kotlinGroupId,
                chatRoomId: chatRoomId,
                lastReadMessageId: lastReadMessageId
            )
        }
    }

    init(
        groupId: Int64?,
        chatRoomId: Int64,
        getChatMessagesUseCase: GetChatMessagesUseCase,
        sendChatMessageUseCase: SendChatMessageUseCase,
        markChatMessagesReadUseCase: MarkChatMessagesReadUseCase,
        observeChatRoomEventsUseCase: ObserveChatRoomEventsUseCase,
        getCurrentUserIdUseCase: GetCurrentUserIdUseCase
    ) {
        self.groupId = groupId
        self.chatRoomId = chatRoomId
        self.getChatMessagesUseCase = getChatMessagesUseCase
        self.sendChatMessageUseCase = sendChatMessageUseCase
        self.markChatMessagesReadUseCase = markChatMessagesReadUseCase
        uiState = UiState(myUserId: getCurrentUserIdUseCase.invoke()?.int64Value)

        loadLatest()
        // 구독 수명 = VM 수명(cancellables) — 화면을 떠나면(pop) 소켓도 함께 닫힌다
        KotlinFlowPublisher<ChatEvent> { onEach in
            observeChatRoomEventsUseCase.eventsFlow(chatRoomId: chatRoomId).subscribe(onEach: onEach)
        }
        .sink { [weak self] in self?.handleEvent($0) }
        .store(in: &cancellables)
    }

    struct UiState {
        /// 말풍선 내/타인 정렬 기준(JWT sub) — 세션이 있는 한 nil이 아니다
        var myUserId: Int64? = nil
        /// 서버 응답 그대로 최신순 — 화면이 뒤집어 그린다(첫 항목 = 맨 아래 최신)
        var messages: [ChatMessage] = []
        var isLoading = false
        var isLoadingOlder = false
        /// 마지막으로 읽은 페이지가 꽉 찼으면 더 오래된 메시지가 남아있다고 본다
        var canLoadOlder = false
        var isSending = false
        /// 이력 로드 에러 — 목록이 비었을 때만 화면을 대체한다
        var error: String? = nil
        /// 전송/이전 로드 실패 문구 — 목록을 대체하지 않는다(가입 신청 인박스 actionError 패턴)
        var actionError: String? = nil
    }

    enum Action {
        case refresh
        case loadOlder
        case send(text: String)
    }

    enum Event {
        /// 전송 성공 — 화면이 입력을 비우고 맨 아래로 스크롤한다
        case sent
    }

    /// 서버 상한과 동일(웹도 50 고정) — 한 번에 최대한 넓은 공백 메꿈
    private static let pageSize: Int32 = 50
}
