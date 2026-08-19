import Combine
import Foundation
import Shared

/// 채팅 허브 — composeApp ChatViewModel.kt와 1:1 미러.
/// 그룹 채팅방(GET /api/chat-rooms, 라운지 제외)+DM 방(GET /api/dm) 두 목록을 조회한다.
/// 방별 미읽음 수는 서버 집계(unreadCount)를 스냅숏으로 받고, 개인 큐(STOMP) CHAT_MESSAGE
/// 이벤트로 실시간 증가시킨다 — 셸 채팅 탭 뱃지(totalUnread)도 이 값의 합(셸이 소유·주입).
/// 마지막 메시지 미리보기(lastMessage*)도 같은 이벤트로 갱신하고 섹션 안은 최근 활동순으로 유지한다.
/// 열려 있는 방(roomOpened~roomClosed)의 이벤트는 방 화면이 직접 표시·읽음 보고하므로 올리지 않고,
/// 방에서 나오면(roomClosed) 재조회로 내가 보내거나 읽은 메시지를 목록에 반영한다.
/// (Kotlin은 두 목록을 async 병렬 조회하지만 KMP suspend는 메인 스레드 호출 제약이 있어 순차 await)
final class ChatViewModel: MviViewModel {
    typealias Event = Never

    @Published private(set) var uiState = UiState()

    private var cancellables = Set<AnyCancellable>()

    private let getGroupChatRoomsUseCase: GetGroupChatRoomsUseCase

    private let getDirectRoomsUseCase: GetDirectRoomsUseCase

    /// 지금 열려 있는 채팅방 — 그 방의 CHAT_MESSAGE는 뱃지 대상이 아니다
    private var activeRoomId: Int64? = nil

    /// 재연결부터만 재조회를 걸기 위한 가드 — 첫 연결은 init의 초기 로드와 겹친다(채팅방 VM 미러)
    private var hasConnectedOnce = false

    func onAction(_ action: Action) {
        switch action {
        case .refresh: load()
        case .roomOpened(let chatRoomId): onRoomOpened(chatRoomId: chatRoomId)
        case .roomClosed(let chatRoomId): onRoomClosed(chatRoomId: chatRoomId)
        }
    }

    private func load() {
        if uiState.isLoading { return }

        uiState.isLoading = true
        uiState.error = nil
        Task { @MainActor in
            do {
                let groupRooms = try await getGroupChatRoomsUseCase.invoke()
                let directRooms = try await getDirectRoomsUseCase.invoke()
                uiState.isLoading = false
                // 섹션 안은 최근 활동순(카카오톡 관례) — 같은 서버가 같은 오프셋으로 주는 ISO-8601이라 문자열 내림차순=최신순
                uiState.groupRooms = groupRooms.sorted { ($0.lastMessageAt ?? $0.createdAt) > ($1.lastMessageAt ?? $1.createdAt) }
                uiState.directRooms = directRooms.sorted { ($0.lastMessageAt ?? $0.createdAt) > ($1.lastMessageAt ?? $1.createdAt) }
            } catch {
                uiState.isLoading = false
                uiState.error = error.kotlinMessage(fallback: "채팅방을 불러오지 못했습니다.")
            }
        }
    }

    /// 방 진입 — 방 화면이 최신 메시지를 읽고 자동 보고하므로 미읽음을 낙관적으로 0 처리한다
    private func onRoomOpened(chatRoomId: Int64) {
        activeRoomId = chatRoomId
        setUnreadCount(chatRoomId: chatRoomId) { _ in 0 }
    }

    private func onRoomClosed(chatRoomId: Int64) {
        if activeRoomId == chatRoomId { activeRoomId = nil }
        // 방에 있는 동안의 활동(내 전송·읽음, 남의 메시지)은 이벤트를 올리지 않았으므로 재조회로 반영한다
        load()
    }

    /// 개인 큐 실시간 이벤트 — 목록에 있는 방이면 미읽음 +1, 모르는 방(새 DM 등)이면 목록 재조회
    private func handlePersonalEvent(_ personalEvent: PersonalEvent) {
        switch personalEvent.type {
        case .connected:
            // 재연결이면 끊김 공백에 놓친 이벤트를 서버 집계 재조회로 메꾼다
            if hasConnectedOnce { load() }
            hasConnectedOnce = true
        case .chatMessage:
            guard let roomId = personalEvent.chatRoomId?.int64Value else { return }
            if roomId == activeRoomId { return }
            if uiState.groupRooms.contains(where: { $0.id == roomId })
                || uiState.directRooms.contains(where: { $0.id == roomId }) {
                applyIncomingMessage(chatRoomId: roomId, event: personalEvent)
            } else {
                load()
            }
        // NOTIFICATION은 알림 VM 소관, DISCONNECTED는 재연결 CONNECTED가 정리한다
        default:
            break
        }
    }

    /// Kotlin data class copy 미러 — ObjC 브리지엔 copy가 없어 전체 생성자로 다시 만든다
    private func setUnreadCount(chatRoomId: Int64, _ transform: (Int64) -> Int64) {
        uiState.groupRooms = uiState.groupRooms.map { room in
            guard room.id == chatRoomId else { return room }
            return GroupChatRoom(
                id: room.id,
                groupId: room.groupId,
                groupName: room.groupName,
                name: room.name,
                createdAt: room.createdAt,
                unreadCount: transform(room.unreadCount),
                lastMessageText: room.lastMessageText,
                lastMessageType: room.lastMessageType,
                lastMessageAt: room.lastMessageAt
            )
        }
        uiState.directRooms = uiState.directRooms.map { room in
            guard room.id == chatRoomId else { return room }
            return DirectRoom(
                id: room.id,
                otherUserId: room.otherUserId,
                otherUserName: room.otherUserName,
                otherUserProfileImg: room.otherUserProfileImg,
                createdAt: room.createdAt,
                unreadCount: transform(room.unreadCount),
                lastMessageText: room.lastMessageText,
                lastMessageType: room.lastMessageType,
                lastMessageAt: room.lastMessageAt
            )
        }
    }

    /// 목록에 있는 방의 새 메시지 — 미읽음 +1에 더해 미리보기를 갱신하고 최근 활동순을 다시 맞춘다
    private func applyIncomingMessage(chatRoomId: Int64, event: PersonalEvent) {
        // 구서버 이벤트(미리보기 필드 없음)면 미읽음만 올린다 — createdAt 유무로 판별
        let hasPreview = event.createdAt != nil
        uiState.groupRooms = uiState.groupRooms.map { room in
            guard room.id == chatRoomId else { return room }
            return GroupChatRoom(
                id: room.id,
                groupId: room.groupId,
                groupName: room.groupName,
                name: room.name,
                createdAt: room.createdAt,
                unreadCount: room.unreadCount + 1,
                lastMessageText: hasPreview ? event.text : room.lastMessageText,
                lastMessageType: hasPreview ? event.attachmentType : room.lastMessageType,
                lastMessageAt: hasPreview ? event.createdAt : room.lastMessageAt
            )
        }.sorted { ($0.lastMessageAt ?? $0.createdAt) > ($1.lastMessageAt ?? $1.createdAt) }
        uiState.directRooms = uiState.directRooms.map { room in
            guard room.id == chatRoomId else { return room }
            return DirectRoom(
                id: room.id,
                otherUserId: room.otherUserId,
                otherUserName: room.otherUserName,
                otherUserProfileImg: room.otherUserProfileImg,
                createdAt: room.createdAt,
                unreadCount: room.unreadCount + 1,
                lastMessageText: hasPreview ? event.text : room.lastMessageText,
                lastMessageType: hasPreview ? event.attachmentType : room.lastMessageType,
                lastMessageAt: hasPreview ? event.createdAt : room.lastMessageAt
            )
        }.sorted { ($0.lastMessageAt ?? $0.createdAt) > ($1.lastMessageAt ?? $1.createdAt) }
    }

    init(
        getGroupChatRoomsUseCase: GetGroupChatRoomsUseCase,
        getDirectRoomsUseCase: GetDirectRoomsUseCase,
        observePersonalEventsUseCase: ObservePersonalEventsUseCase
    ) {
        self.getGroupChatRoomsUseCase = getGroupChatRoomsUseCase
        self.getDirectRoomsUseCase = getDirectRoomsUseCase
        load()
        KotlinFlowPublisher<PersonalEvent> { onEach in
            observePersonalEventsUseCase.eventsFlow().subscribe(onEach: onEach)
        }
        .sink { [weak self] in self?.handlePersonalEvent($0) }
        .store(in: &cancellables)
    }

    struct UiState {
        var groupRooms: [GroupChatRoom] = []
        var directRooms: [DirectRoom] = []
        var isLoading = false
        var error: String? = nil

        /// 셸 채팅 탭 뱃지 — 모든 방 미읽음의 합
        var totalUnread: Int64 {
            groupRooms.reduce(0) { $0 + $1.unreadCount } + directRooms.reduce(0) { $0 + $1.unreadCount }
        }
    }

    enum Action {
        case refresh

        /// 채팅방 화면 진입/이탈 신호 — 진입한 방은 미읽음 0 처리 후 실시간 증가에서 제외한다
        case roomOpened(chatRoomId: Int64)
        case roomClosed(chatRoomId: Int64)
    }
}
