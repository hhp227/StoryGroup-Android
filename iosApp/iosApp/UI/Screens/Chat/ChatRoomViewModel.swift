import Combine
import Foundation
import Shared

/// 채팅방 — composeApp ChatRoomViewModel.kt와 1:1 미러.
/// 이력은 REST 최신순 오프셋 페이징, 실시간 수신은 STOMP 구독(VM 수명 = 소켓 수명),
/// 전송·읽음 보고는 REST(웹 미러). 메시지 목록은 서버 응답 그대로 최신순으로 들고
/// 화면이 뒤집어 그린다. 재연결(connected)·연결 유실(disconnected) 시 최신 페이지를
/// 다시 읽어 끊김 공백을 메꾼다 — 유실 시 REST 재조회는 만료 토큰 리프레시 역할을 겸한다.
/// 첨부는 전송 시점 업로드(웹 미러), 타이핑은 스로틀 발신+수신 자동 소멸,
/// "읽음 N"은 멤버별 읽음 위치에서 파생한다(전부 웹 미러).
final class ChatRoomViewModel: MviViewModel {
    @Published private(set) var uiState: UiState

    let event = PassthroughSubject<Event, Never>()

    private var cancellables = Set<AnyCancellable>()

    /// 통화 로스터 폴링 — deinit에서 취소한다(Task는 cancellables에 못 담는다)
    private var rosterTask: Task<Void, Never>?

    private let chatRoomId: Int64

    private let groupId: Int64?

    private let getChatMessagesUseCase: GetChatMessagesUseCase

    private let sendChatMessageUseCase: SendChatMessageUseCase

    private let markChatMessagesReadUseCase: MarkChatMessagesReadUseCase

    private let uploadChatFileUseCase: UploadChatFileUseCase

    private let sendChatTypingUseCase: SendChatTypingUseCase

    private let getChatReadPositionsUseCase: GetChatReadPositionsUseCase

    private let getGroupMembersUseCase: GetGroupMembersUseCase

    /// 최신순 페이징 커서 — 재조회(loadLatest)마다 0으로 되돌아간다(웹 전체 교체 미러)
    private var oldestLoadedPage: Int32 = 0

    /// 첫 connected는 init 로드와 겹치므로 재연결부터 공백 메꿈 재조회를 한다
    private var hasConnectedOnce = false

    /// 타이핑 발신 스로틀 기준점 — 첫 키 입력은 즉시 나간다(리딩 에지, 웹 미러)
    private var lastTypingSentAt: Date?

    /// 타이핑 수신자별 자동 소멸 타이머 — 같은 사람의 신호가 오면 리셋된다
    private var typingExpiryTasks: [Int64: Task<Void, Never>] = [:]

    /// Kotlin `groupId: Long?` 파라미터 대응 — nil이면 DM 경로(/api/dm)를 탄다
    private var kotlinGroupId: KotlinLong? { groupId.map { KotlinLong(value: $0) } }

    func onAction(_ action: Action) {
        switch action {
        case .refresh: loadLatest()
        case .loadOlder: loadOlder()
        case .send(let text): send(text: text)
        case .attach(let data, let fileName, let contentType):
            uiState.pendingAttachment = PendingAttachment(data: data, fileName: fileName, contentType: contentType)
            uiState.actionError = nil
        case .attachVideo(let picked): attachVideo(picked: picked)
        case .clearAttachment: uiState.pendingAttachment = nil
        case .typing: sendTypingThrottled()
        case .loadMembers: loadMembers()
        }
    }

    /// 대화상대 = 그룹 멤버(그룹 방 전용) — 채팅방 참여자 API가 없어 그룹 멤버로 대신한다.
    /// DM 방은 참여자가 나와 상대뿐이라 서버를 부르지 않고 화면이 메시지에서 파생한다.
    /// 드로어를 처음 열 때 1회만 — 실패해도 조용히 둔다(드로어가 "불러오지 못했습니다"를 그린다)
    private func loadMembers() {
        guard let groupId = groupId, !uiState.isLoadingMembers, uiState.members == nil else { return }

        uiState.isLoadingMembers = true
        Task { @MainActor in
            uiState.members = try? await getGroupMembersUseCase.invoke(groupId: groupId)
            uiState.isLoadingMembers = false
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
                // 읽음 위치도 함께 새로 고침 — 재연결 시 끊김 동안의 READ 이벤트 공백을 메꾼다
                loadReadPositions()
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

    /// 채팅 동영상 첨부(§4-b) — 판정(§2)은 선택 즉시, 압축은 백그라운드 큐, 업로드는 기존대로 전송 시점.
    /// 완료되면 대기 첨부가 압축본(upload.mp4, ≤5MB)으로 채워진다. Compose attachVideo 미러.
    private func attachVideo(picked: PickedVideo) {
        let plan = VideoCompressionPlanner.shared.plan(
            durationMs: picked.durationMs, sizeBytes: picked.sizeBytes,
            width: picked.width, height: picked.height,
            margin: VideoCompressionPlanner.shared.FIRST_MARGIN
        )
        if plan is VideoPlanRejectTooLarge {
            uiState.actionError = "파일이 너무 큽니다. (최대 500MB)"
        } else if plan is VideoPlanRejectTooLong {
            uiState.actionError = "동영상은 최대 3분까지 첨부할 수 있습니다."
        } else if plan is VideoPlanSkipAlreadySmall {
            let bytes = (try? Data(contentsOf: picked.url)) ?? Data()
            try? FileManager.default.removeItem(at: picked.url)
            uiState.pendingAttachment = PendingAttachment(data: bytes, fileName: picked.fileName, contentType: picked.contentType)
            uiState.actionError = nil
        } else if let compress = plan as? VideoPlanCompress {
            compressAndAttach(picked: picked, plan: compress, isRetry: false)
        }
    }

    private func compressAndAttach(picked: PickedVideo, plan: VideoPlanCompress, isRetry: Bool) {
        uiState.compressionProgress = 0
        uiState.actionError = nil
        MediaCompressionQueue.shared.compressVideo(
            inputURL: picked.url,
            plan: plan,
            onProgress: { [weak self] fraction in self?.uiState.compressionProgress = fraction }
        ) { [weak self] result in
            guard let self = self else { return }
            switch result {
            case .failure:
                try? FileManager.default.removeItem(at: picked.url)
                self.uiState.compressionProgress = nil
                self.uiState.actionError = "동영상 압축에 실패했습니다."
            case .success(let outputURL):
                let bytes = (try? Data(contentsOf: outputURL)) ?? Data()
                try? FileManager.default.removeItem(at: outputURL)
                if Int64(bytes.count) <= VideoCompressionPlanner.shared.TARGET_BYTES {
                    try? FileManager.default.removeItem(at: picked.url)
                    self.uiState.compressionProgress = nil
                    // 출력은 항상 MP4(§2) — 업로드는 전송 시점(send)에 나간다
                    self.uiState.pendingAttachment = PendingAttachment(data: bytes, fileName: "upload.mp4", contentType: "video/mp4")
                } else if !isRetry {
                    // 단일 패스 ABR 오버슈트 — 더 보수적인 마진으로 딱 한 번 재시도(§2-5)
                    let retry = VideoCompressionPlanner.shared.plan(
                        durationMs: picked.durationMs, sizeBytes: picked.sizeBytes,
                        width: picked.width, height: picked.height,
                        margin: VideoCompressionPlanner.shared.RETRY_MARGIN
                    )
                    if let retryPlan = retry as? VideoPlanCompress {
                        self.compressAndAttach(picked: picked, plan: retryPlan, isRetry: true)
                    } else {
                        try? FileManager.default.removeItem(at: picked.url)
                        self.uiState.compressionProgress = nil
                        self.uiState.actionError = "동영상 압축에 실패했습니다."
                    }
                } else {
                    try? FileManager.default.removeItem(at: picked.url)
                    self.uiState.compressionProgress = nil
                    self.uiState.actionError = "동영상 압축에 실패했습니다."
                }
            }
        }
    }

    private func send(text: String) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        let pending = uiState.pendingAttachment

        // 첨부가 있으면 본문 없이도 보낼 수 있다(웹 미러 — 서버는 둘 다 비었을 때만 400)
        // 압축이 끝나기 전에 보내면 첨부가 빠진 채 나간다 — 완료까지 전송을 막는다(§4-b)
        if (trimmed.isEmpty && pending == nil) || uiState.isSending || uiState.compressionProgress != nil { return }

        uiState.isSending = true
        uiState.actionError = nil
        Task { @MainActor in
            do {
                // 첨부는 전송 시점에 업로드한다 — 선택만 하고 안 보내면 스토리지에 고아가 안 남는다(웹 미러)
                var attachment: ChatAttachment?
                if let pending {
                    attachment = try await uploadChatFileUseCase.invoke(
                        bytes: pending.data.toKotlinByteArray(),
                        fileName: pending.fileName,
                        contentType: pending.contentType
                    )
                }
                let message = try await sendChatMessageUseCase.invoke(
                    groupId: kotlinGroupId,
                    chatRoomId: chatRoomId,
                    text: trimmed,
                    attachment: attachment
                )
                uiState.isSending = false
                uiState.pendingAttachment = nil
                // STOMP 브로드캐스트가 먼저 도착했을 수 있어 id 중복 제거를 거친다(웹 미러)
                appendMessage(message)
                event.send(.sent)
            } catch {
                // 첨부는 유지 — 업로드/전송 실패 시 같은 첨부로 재시도할 수 있다(웹 미러)
                uiState.isSending = false
                uiState.actionError = error.kotlinMessage(fallback: "전송에 실패했습니다.")
            }
        }
    }

    /// 타이핑 신호 스로틀 발신 — 2.5초에 한 번, 발신 실패·미연결은 조용히 버려진다(웹 미러)
    private func sendTypingThrottled() {
        if let last = lastTypingSentAt, Date().timeIntervalSince(last) < Self.typingSendInterval { return }

        lastTypingSentAt = Date()
        Task { @MainActor in
            try? await sendChatTypingUseCase.invoke(chatRoomId: chatRoomId)
        }
    }

    /// 타이핑 수신 — 4초 무신호면 지운다. 발신 간격(2.5초) < 소멸(4초)이라 깜빡이지 않는다(웹 미러)
    private func noteTypist(userId: Int64, userName: String) {
        uiState.typists[userId] = userName
        typingExpiryTasks[userId]?.cancel()
        typingExpiryTasks[userId] = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: Self.typingHideNanos)
            if Task.isCancelled { return }
            self?.typingExpiryTasks[userId] = nil
            self?.uiState.typists[userId] = nil
        }
    }

    private func clearTypist(userId: Int64) {
        typingExpiryTasks[userId]?.cancel()
        typingExpiryTasks[userId] = nil
        if uiState.typists[userId] != nil { uiState.typists[userId] = nil }
    }

    private func handleEvent(_ chatEvent: ChatEvent) {
        switch chatEvent.type {
        case .connected:
            if hasConnectedOnce { loadLatest() } else { hasConnectedOnce = true }
        case .disconnected:
            loadLatest()
        case .messageCreated:
            if let message = chatEvent.message {
                // 메시지가 도착했으면 그 사람의 "입력 중"은 소멸 타이머를 기다리지 않고 즉시 걷는다(웹 미러)
                clearTypist(userId: message.userId)
                appendMessage(message)
            }
        case .messageUpdated:
            if let updated = chatEvent.message {
                uiState.messages = uiState.messages.map { $0.id == updated.id ? updated : $0 }
            }
        case .messageDeleted:
            if let deletedId = chatEvent.messageId?.int64Value {
                uiState.messages.removeAll { $0.id == deletedId }
            }
        case .typing:
            // 서버는 발신자 본인에게도 릴레이한다 — 내 타이핑은 거른다(웹 미러)
            if let userId = chatEvent.userId?.int64Value, userId != uiState.myUserId {
                noteTypist(userId: userId, userName: chatEvent.userName ?? "")
            }
        case .read:
            // READ의 messageId는 "그 사람의 마지막 읽음 위치" — 순서 보장이 없어 max 병합(웹 미러)
            if let userId = chatEvent.userId?.int64Value,
               let lastReadMessageId = chatEvent.messageId?.int64Value,
               lastReadMessageId > (uiState.readPositions[userId] ?? 0) {
                uiState.readPositions[userId] = lastReadMessageId
            }
        // 프레즌스("보고 있어요") 표시는 후속
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

    /// 멤버별 읽음 위치 스냅숏 — 실패해도 치명적이지 않아 조용히 넘어간다(이후 READ 이벤트가 채운다)
    private func loadReadPositions() {
        Task { @MainActor in
            guard let positions = try? await getChatReadPositionsUseCase.invoke(
                groupId: kotlinGroupId,
                chatRoomId: chatRoomId
            ) else { return }

            // 조회 중 도착한 READ 이벤트가 응답보다 새것일 수 있어 max 병합
            for position in positions {
                uiState.readPositions[position.userId] =
                    max(uiState.readPositions[position.userId] ?? 0, position.lastReadMessageId)
            }
        }
    }

    init(
        groupId: Int64?,
        chatRoomId: Int64,
        getChatMessagesUseCase: GetChatMessagesUseCase = AppContainer.shared.getChatMessagesUseCase,
        sendChatMessageUseCase: SendChatMessageUseCase = AppContainer.shared.sendChatMessageUseCase,
        markChatMessagesReadUseCase: MarkChatMessagesReadUseCase = AppContainer.shared.markChatMessagesReadUseCase,
        uploadChatFileUseCase: UploadChatFileUseCase = AppContainer.shared.uploadChatFileUseCase,
        sendChatTypingUseCase: SendChatTypingUseCase = AppContainer.shared.sendChatTypingUseCase,
        getChatReadPositionsUseCase: GetChatReadPositionsUseCase = AppContainer.shared.getChatReadPositionsUseCase,
        getGroupMembersUseCase: GetGroupMembersUseCase = AppContainer.shared.getGroupMembersUseCase,
        getCallRosterUseCase: GetCallRosterUseCase = AppContainer.shared.getCallRosterUseCase,
        observeChatRoomEventsUseCase: ObserveChatRoomEventsUseCase = AppContainer.shared.observeChatRoomEventsUseCase,
        getCurrentUserIdUseCase: GetCurrentUserIdUseCase = AppContainer.shared.getCurrentUserIdUseCase
    ) {
        self.groupId = groupId
        self.chatRoomId = chatRoomId
        self.getChatMessagesUseCase = getChatMessagesUseCase
        self.sendChatMessageUseCase = sendChatMessageUseCase
        self.markChatMessagesReadUseCase = markChatMessagesReadUseCase
        self.uploadChatFileUseCase = uploadChatFileUseCase
        self.sendChatTypingUseCase = sendChatTypingUseCase
        self.getChatReadPositionsUseCase = getChatReadPositionsUseCase
        self.getGroupMembersUseCase = getGroupMembersUseCase
        uiState = UiState(myUserId: getCurrentUserIdUseCase.invoke()?.int64Value)

        loadLatest()
        // 구독 수명 = VM 수명(cancellables) — 화면을 떠나면(pop) 소켓도 함께 닫힌다
        KotlinFlowPublisher<ChatEvent> { onEach in
            observeChatRoomEventsUseCase.eventsFlow(chatRoomId: chatRoomId).subscribe(onEach: onEach)
        }
        .sink { [weak self] in self?.handleEvent($0) }
        .store(in: &cancellables)
        // 통화 진행 중 라이브 바용 로스터 폴링(웹 라이브 카드와 같은 6초 주기) —
        // 부가 정보라 실패는 조용히 넘어가고, VM 수명 = 화면 수명이라 pop되면 함께 멈춘다
        rosterTask = Task { @MainActor [weak self] in
            while !Task.isCancelled {
                if let roster = try? await getCallRosterUseCase.invoke(chatRoomId: chatRoomId) {
                    self?.uiState.callRoster = roster
                }
                try? await Task.sleep(nanoseconds: Self.callRosterPollNanos)
            }
        }
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
        /// 이 방에서 통화 중인 사람(6초 폴링 스냅숏) — 비어 있지 않으면 상단 라이브 바가 뜬다
        var callRoster: [RtcCallPeer] = []
        /// 전송 대기 첨부(메시지당 1개, 전송 시점 업로드) — 실패해도 유지돼 재시도할 수 있다
        var pendingAttachment: PendingAttachment? = nil
        /// 동영상 압축 진행률(0..1) — nil이면 압축 중 아님. 압축 중엔 전송 비활성(§4-b)
        var compressionProgress: Float? = nil
        /// 입력 중인 타인(userId→이름) — 신호가 끊기면 4초 뒤 자동 소멸
        var typists: [Int64: String] = [:]
        /// 멤버별 마지막 읽음 위치(userId→messageId, 본인 포함) — "읽음 N"은 화면이 파생한다
        var readPositions: [Int64: Int64] = [:]
        /// 이력 로드 에러 — 목록이 비었을 때만 화면을 대체한다
        var error: String? = nil
        /// 전송/이전 로드 실패 문구 — 목록을 대체하지 않는다(가입 신청 인박스 actionError 패턴)
        var actionError: String? = nil
        /// 우측 드로어의 대화상대 — 그룹 방만 채워진다(DM은 참여자가 둘뿐이라 화면이 파생).
        /// nil = 아직 안 읽음(드로어를 열어야 읽는다), [] = 읽었는데 비어 있음
        var members: [GroupMember]? = nil
        var isLoadingMembers = false
    }

    /// 전송 대기 첨부 — Compose PendingAttachment 미러
    struct PendingAttachment {
        let data: Data
        let fileName: String
        let contentType: String

        var isImage: Bool { contentType.hasPrefix("image/") }
    }

    enum Action {
        case refresh
        case loadOlder
        case send(text: String)
        /// 피커 선택 결과 — 업로드는 전송 시점까지 미룬다
        case attach(data: Data, fileName: String, contentType: String)
        /// 파일 피커에서 고른 동영상 — 선택 시점에 압축(§4-b), 업로드는 전송 시점
        case attachVideo(picked: PickedVideo)
        case clearAttachment
        /// 입력 변화 신호 — VM이 스로틀해 STOMP 타이핑 신호로 발신한다
        case typing
        /// 우측 드로어 첫 오픈 — 그룹 방의 대화상대(그룹 멤버)를 1회 읽는다
        case loadMembers
    }

    enum Event {
        /// 전송 성공 — 화면이 입력을 비우고 맨 아래로 스크롤한다
        case sent
    }

    deinit {
        // 화면 소멸 시 진행 중 압축 취소(§11) — Compose는 viewModelScope 취소가 같은 역할
        MediaCompressionQueue.shared.cancelAll()
        typingExpiryTasks.values.forEach { $0.cancel() }
        rosterTask?.cancel()
    }

    /// 서버 상한과 동일(웹도 50 고정) — 한 번에 최대한 넓은 공백 메꿈
    private static let pageSize: Int32 = 50

    /// 웹 TYPING_SEND_INTERVAL_MS/TYPING_HIDE_MS 미러 — 발신 간격 < 소멸 시간이라 연속 입력 중 깜빡이지 않는다
    private static let typingSendInterval: TimeInterval = 2.5

    private static let typingHideNanos: UInt64 = 4_000_000_000

    /// 웹 라이브 카드(usePolling 6000ms)와 같은 주기 — 라이브 바는 미리보기라 즉시성이 덜 중요하다
    private static let callRosterPollNanos: UInt64 = 6_000_000_000
}
