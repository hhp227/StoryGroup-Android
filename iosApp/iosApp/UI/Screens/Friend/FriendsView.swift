import SwiftUI
import Shared

/// 친구 탭 — 웹 /search(검색 전=친구 목록, 검색 후=사용자 검색 결과, 카카오톡 친구 탭 패턴)·Compose FriendsScreen 미러.
/// 검색바는 콘텐츠 상단 인라인 — keep-alive ZStack에서 .searchable은 숨은 탭의 것까지 내비바에 새서 쓰지 않는다.
/// 행 탭은 무동작(공개 프로필 화면은 범위 제외) — 액션은 메시지(DM)/해제 버튼 2개.
/// 계층은 Compose와 1:1 — View=상태 소유(VM 선언), Content=구독+UI.
struct FriendsView: View {
    @StateObject private var viewModel: FriendsViewModel

    /// 채팅방 풀스크린 push — MainShellView(루트 NavigationStack)로 위임
    let onOpenChatRoom: (ChatRoomRef) -> Void

    var body: some View {
        FriendsContent(viewModel: viewModel, onOpenChatRoom: onOpenChatRoom)
    }

    init(container: AppContainer, onOpenChatRoom: @escaping (ChatRoomRef) -> Void) {
        _viewModel = StateObject(wrappedValue: FriendsViewModel(
            getFriendsUseCase: container.getFriendsUseCase,
            addFriendUseCase: container.addFriendUseCase,
            removeFriendUseCase: container.removeFriendUseCase,
            searchUsersUseCase: container.searchUsersUseCase,
            openDirectRoomUseCase: container.openDirectRoomUseCase,
            observePersonalEventsUseCase: container.observePersonalEventsUseCase
        ))
        self.onOpenChatRoom = onOpenChatRoom
    }
}

private struct FriendsContent: View {
    @ObservedObject var viewModel: FriendsViewModel

    let onOpenChatRoom: (ChatRoomRef) -> Void

    @Environment(\.sgColors) private var colors

    @State private var queryText = ""

    /// 해제 확인 다이얼로그 대상 — 실수 탭 방지(웹은 즉시 해제지만 모바일은 확인을 거친다)
    @State private var removeTarget: Friend? = nil

    var body: some View {
        VStack(spacing: 0) {
            searchBar
                .padding(.horizontal, 16)
                .padding(.top, 16)
            if let actionError = viewModel.uiState.actionError {
                errorText(actionError)
            }
            if let searchError = viewModel.uiState.searchError {
                errorText(searchError)
            }
            content
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(colors.paper)
        .onReceive(viewModel.event) { event in
            switch event {
            case .dmOpened(let chatRoomId, let title):
                onOpenChatRoom(ChatRoomRef(chatRoomId: chatRoomId, groupId: nil, title: title))
            }
        }
        .overlay {
            if let friend = removeTarget {
                RemoveFriendDialog(
                    friend: friend,
                    onDismiss: { removeTarget = nil },
                    onConfirm: {
                        removeTarget = nil
                        viewModel.onAction(.removeFriend(userId: friend.userId))
                    }
                )
            }
        }
    }

    /// 인라인 검색바 — 실행은 키보드 검색(제출 기반), 지우면 즉시 친구 목록 복귀(Compose SearchBar 미러)
    private var searchBar: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 16))
                .foregroundColor(colors.inkFaint)
            TextField("이름으로 사용자 검색", text: $queryText)
                .font(.subheadline)
                .foregroundColor(colors.ink)
                .submitLabel(.search)
                .onSubmit { viewModel.onAction(.search(query: queryText)) }
                .autocapitalization(.none)
                .disableAutocorrection(true)
            if !queryText.isEmpty {
                Button(action: { queryText = "" }) {
                    Image(systemName: "xmark.circle.fill").foregroundColor(colors.inkFaint)
                }
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(
            RoundedRectangle(cornerRadius: colors.radiusButton ?? 12, style: .continuous)
                .fill(colors.linen)
        )
        // 비우면(지우기 버튼 포함) 즉시 친구 목록 복귀 — 웹의 "복귀 불가"는 미러하지 않는다
        .onChange(of: queryText) { if $0.isEmpty { viewModel.onAction(.search(query: "")) } }
    }

    private func errorText(_ message: String) -> some View {
        Text(message)
            .font(.caption)
            .foregroundColor(colors.rust)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 16)
            .padding(.top, 8)
    }

    @ViewBuilder private var content: some View {
        let uiState = viewModel.uiState

        if uiState.isSearching {
            ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let results = uiState.searchResults {
            // 검색 후 — 사용자 검색 결과(친구 추가/해제 토글)가 친구 목록 자리를 대체한다
            searchResultList(results: results, friendIds: uiState.friendIds)
        } else if uiState.isLoading, uiState.friends.isEmpty {
            ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let error = uiState.error, uiState.friends.isEmpty {
            VStack(spacing: 8) {
                Text(error).font(.subheadline).foregroundColor(colors.rust)
                Button("다시 시도") { viewModel.onAction(.refresh) }
                    .font(.subheadline)
                    .foregroundColor(colors.accent)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if uiState.friends.isEmpty {
            SGEmptyState(title: "아직 친구가 없습니다", subtitle: "위 검색으로 사용자를 찾아 친구를 추가해보세요.")
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            ScrollView {
                VStack(spacing: 8) {
                    ForEach(uiState.friends, id: \.userId) { friend in
                        FriendRow(
                            friend: friend,
                            isBusy: uiState.isOpeningDm || uiState.processingUserId != nil,
                            onOpenDm: { viewModel.onAction(.openDm(userId: friend.userId, userName: friend.name)) },
                            onRemove: { removeTarget = friend }
                        )
                    }
                }
                .padding(16)
            }
        }
    }

    /// 검색 결과 목록 — 같은 그룹 소속 사용자만 나온다(서버 필터, 본인·차단 제외)
    @ViewBuilder private func searchResultList(results: [UserSearchResult], friendIds: Set<Int64>) -> some View {
        if results.isEmpty {
            SGEmptyState(title: "검색 결과가 없습니다", subtitle: "같은 그룹에 소속된 사용자만 검색됩니다.")
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            ScrollView {
                VStack(spacing: 8) {
                    ForEach(results, id: \.id) { user in
                        SearchResultRow(
                            user: user,
                            isFriend: friendIds.contains(user.id),
                            isProcessing: viewModel.uiState.processingUserId == user.id,
                            enabled: viewModel.uiState.processingUserId == nil,
                            onAddFriend: { viewModel.onAction(.addFriend(user: user)) },
                            onRemoveFriend: { viewModel.onAction(.removeFriend(userId: user.id)) }
                        )
                    }
                }
                .padding(16)
            }
        }
    }
}

/// 친구 행 — 웹 친구 카드 미러(이름+상태메시지, 메시지/해제 버튼 2개). 행 탭은 무동작
private struct FriendRow: View {
    let friend: Friend

    let isBusy: Bool

    let onOpenDm: () -> Void

    let onRemove: () -> Void

    @Environment(\.sgColors) private var colors

    var body: some View {
        SGCard {
            HStack(spacing: 12) {
                ZStack(alignment: .bottomTrailing) {
                    SGAvatar(name: friend.name, imageUrl: friend.profileImg)
                    // 온라인 도트 — 친구 탭 전용이라 공용 SGAvatar는 건드리지 않는다(Compose FriendRow 미러)
                    if friend.online {
                        ZStack {
                            Circle().fill(colors.linen).frame(width: 14, height: 14)
                            Circle()
                                .fill(Color(red: 52 / 255, green: 199 / 255, blue: 89 / 255))
                                .frame(width: 10, height: 10)
                        }
                    }
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text(friend.name).font(.subheadline.bold()).foregroundColor(colors.ink)
                    if let statusMessage = friend.statusMessage, !statusMessage.isEmpty {
                        Text(statusMessage).font(.caption).foregroundColor(colors.inkFaint)
                    }
                }
                Spacer()
                Button("메시지", action: onOpenDm)
                    .font(.subheadline.bold())
                    .foregroundColor(colors.accent)
                    .disabled(isBusy)
                Button("해제", action: onRemove)
                    .font(.subheadline.bold())
                    .foregroundColor(colors.inkSoft)
                    .disabled(isBusy)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
    }
}

private struct SearchResultRow: View {
    let user: UserSearchResult

    let isFriend: Bool

    let isProcessing: Bool

    let enabled: Bool

    let onAddFriend: () -> Void

    let onRemoveFriend: () -> Void

    @Environment(\.sgColors) private var colors

    var body: some View {
        SGCard {
            HStack(spacing: 12) {
                SGAvatar(name: user.name, imageUrl: user.profileImg)
                VStack(alignment: .leading, spacing: 2) {
                    Text(user.name).font(.subheadline.bold()).foregroundColor(colors.ink)
                    if let statusMessage = user.statusMessage, !statusMessage.isEmpty {
                        Text(statusMessage).font(.caption).foregroundColor(colors.inkFaint)
                    }
                }
                Spacer()
                if isProcessing {
                    ProgressView()
                } else if isFriend {
                    Button(action: onRemoveFriend) {
                        Text("친구 해제")
                            .font(.subheadline.bold())
                            .foregroundColor(colors.inkSoft)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background(
                                RoundedRectangle(cornerRadius: colors.radiusButton ?? 12, style: .continuous)
                                    .stroke(colors.stoneBorder, lineWidth: 1)
                            )
                    }
                    .buttonStyle(.plain)
                    .disabled(!enabled)
                } else {
                    Button(action: onAddFriend) {
                        Text("친구 추가")
                            .font(.subheadline.bold())
                            .foregroundColor(colors.onAccent)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background(
                                RoundedRectangle(cornerRadius: colors.radiusButton ?? 12, style: .continuous)
                                    .fill(colors.accent)
                            )
                    }
                    .buttonStyle(.plain)
                    .disabled(!enabled)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
    }
}

/// 해제 확인 다이얼로그 — 단방향 등록이라 상대에게 알림·영향 없음(즐겨찾기 해제 성격).
/// GroupDetailDialog처럼 반투명 배경+중앙 카드로 직접 구현(iOS 15 공통)
private struct RemoveFriendDialog: View {
    let friend: Friend

    let onDismiss: () -> Void

    let onConfirm: () -> Void

    @Environment(\.sgColors) private var colors

    var body: some View {
        ZStack {
            Color.black.opacity(0.35)
                .ignoresSafeArea()
                .onTapGesture(perform: onDismiss)
            SGCard {
                VStack(alignment: .leading, spacing: 12) {
                    Text("친구 해제")
                        .font(.headline)
                        .foregroundColor(colors.ink)
                    Text("\(friend.name)님을 친구에서 해제할까요?")
                        .font(.subheadline)
                        .foregroundColor(colors.inkSoft)
                    HStack(spacing: 16) {
                        Spacer()
                        Button("취소", action: onDismiss).foregroundColor(colors.inkSoft)
                        Button("해제", action: onConfirm).foregroundColor(colors.rust)
                    }
                }
                .padding(16)
            }
            .padding(24)
        }
    }
}
