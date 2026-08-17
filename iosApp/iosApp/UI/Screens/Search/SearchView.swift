import SwiftUI
import Shared

/// 게시글 상세 push 대상 — (groupId, postId) 쌍
private struct PostRef: Equatable {
    let groupId: Int64
    let postId: Int64
}

/// 공개 프로필 시트에서 고른 후속 push 대상 — 시트가 완전히 닫힌 뒤(onDismiss) 실행해야 유실되지 않는다
private enum ProfileFollowUp {
    case chatRoom(ChatRoomRef)
    case accountSettings
}

/// 홈 통합검색 — composeApp SearchScreen.kt와 1:1 미러(제출 기반, 5섹션 원페이지).
/// 결과 push(그룹 상세·게시글 상세·채팅방)는 자체 소유(GroupDetailView 선례),
/// 파일은 시스템 브라우저(openURL), 사용자는 행 탭=공개 프로필 시트(친구 추가/해제는 내부 버튼).
struct SearchView: View {
    let container: AppContainer

    /// 채팅방·그룹 상세 push에 필요 — MainShellView 소유 세션 VM 전달.
    /// 이 화면은 상태를 직접 구독하지 않고 통과만 시킨다 — GroupDetailView 바깥 레이어 선례(plain let)
    let chatViewModel: ChatViewModel

    /// GroupDetailView가 요구 — MainShellView에서 전달. 통과 전용(GroupDetailView 선례, plain let)
    let theme: SGThemeState

    let profileViewModel: ProfileViewModel

    /// 그룹 상세에서 나가기/삭제 시 그룹 탭 refresh 신호 — NavigationViewModel.pendingResults에
    /// NavResult.groupsChanged를 publish한다(TabShellView/DrawerShellView가 소비)
    let onGroupsRefreshNeeded: () -> Void

    @StateObject private var searchViewModel: SearchViewModel

    @State private var queryText = ""

    @State private var selectedGroupId: Int64? = nil

    @State private var selectedPost: PostRef? = nil

    @State private var selectedChatRoom: ChatRoomRef? = nil

    @State private var selectedUserId: Int64? = nil

    /// 프로필 시트에서 본인 "프로필 수정" 후속 push — MainShellView 계정 설정 미러
    @State private var showAccountSettings = false

    /// 프로필 시트의 후속 이동(채팅방/계정 설정) — 시트 dismiss 완료 후 push한다
    @State private var profileFollowUp: ProfileFollowUp? = nil

    @Environment(\.sgColors) private var colors

    @Environment(\.openURL) private var openURL

    var body: some View {
        pushContainer
            .navigationTitle("검색")
            .navigationBarTitleDisplayMode(.inline)
            // 공개 프로필 시트 — Compose dialog<UserProfileRoute> 미러. 후속 이동(채팅방/계정 설정)은
            // 시트가 완전히 닫힌 뒤(onDismiss)에 push해야 유실되지 않는다
            .sheet(isPresented: showUserProfile, onDismiss: runProfileFollowUp) { userProfileDestination }
    }

    /// 자체 push 4종(그룹·게시글·채팅방·계정 설정) — iOS 16 navigationDestination /
    /// iOS 15 숨김 NavigationLink 폴백(GroupDetailView 선례). 공개 프로필은 push가 아니라 시트
    @ViewBuilder private var pushContainer: some View {
        if #available(iOS 16.0, *) {
            content
                .navigationDestination(isPresented: showGroupDetail) { groupDetailDestination }
                .navigationDestination(isPresented: showPostDetail) { postDetailDestination }
                .navigationDestination(isPresented: showChatRoom) { chatRoomDestination }
                .navigationDestination(isPresented: $showAccountSettings) { accountSettingsDestination }
        } else {
            content
                .background(
                    NavigationLink(isActive: showGroupDetail) { groupDetailDestination } label: { EmptyView() }.hidden()
                )
                .background(
                    NavigationLink(isActive: showPostDetail) { postDetailDestination } label: { EmptyView() }.hidden()
                )
                .background(
                    NavigationLink(isActive: showChatRoom) { chatRoomDestination } label: { EmptyView() }.hidden()
                )
                .background(
                    NavigationLink(isActive: $showAccountSettings) { accountSettingsDestination } label: { EmptyView() }.hidden()
                )
        }
    }

    private var content: some View {
        VStack(spacing: 0) {
            searchBar
                .padding(.horizontal, 16)
                .padding(.top, 12)
            if let actionError = searchViewModel.uiState.actionError {
                Text(actionError)
                    .font(.footnote)
                    .foregroundColor(colors.rust)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 16)
                    .padding(.top, 8)
            }
            if let error = searchViewModel.uiState.error {
                HStack {
                    Text(error)
                        .font(.footnote)
                        .foregroundColor(colors.rust)
                    Spacer()
                    Button("다시 시도") { searchViewModel.onAction(.search(query: queryText)) }
                        .font(.footnote.weight(.semibold))
                        .foregroundColor(colors.accent)
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)
            }
            resultsBody
        }
        .background(colors.paper.ignoresSafeArea())
    }

    /// 인라인 검색바 — 친구 탭 검색바 미러(제출 기반, 지우면 초기 복귀)
    private var searchBar: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 16))
                .foregroundColor(colors.inkFaint)
            TextField("그룹, 게시글, 파일, 메시지 검색", text: $queryText)
                .font(.subheadline)
                .foregroundColor(colors.ink)
                .submitLabel(.search)
                .onSubmit { searchViewModel.onAction(.search(query: queryText)) }
                .autocapitalization(.none)
                .disableAutocorrection(true)
                .onChange(of: queryText) { newValue in
                    if newValue.isEmpty { searchViewModel.onAction(.clearResults) }
                }
            if !queryText.isEmpty {
                Button(action: {
                    queryText = ""
                    searchViewModel.onAction(.clearResults)
                }) {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundColor(colors.inkFaint)
                }
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(
            RoundedRectangle(cornerRadius: colors.radiusButton ?? 12, style: .continuous)
                .fill(colors.linen)
        )
    }

    @ViewBuilder private var resultsBody: some View {
        if searchViewModel.uiState.isSearching {
            Spacer()
            ProgressView().tint(colors.accent)
            Spacer()
        } else if let results = searchViewModel.uiState.results {
            if results.isEmpty {
                SGEmptyState(
                    title: "검색 결과가 없습니다",
                    subtitle: "다른 검색어로 다시 시도해보세요."
                )
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                resultList(results)
            }
        } else {
            SGEmptyState(
                title: "무엇이든 찾아보세요",
                subtitle: "그룹, 게시글, 파일, 메시지, 사용자를 검색할 수 있습니다."
            )
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    /// 5섹션 결과 — 순서는 웹 /search 미러(사용자→그룹→게시글→파일→메시지), 빈 섹션 숨김
    private func resultList(_ results: SearchResults) -> some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 8) {
                if !results.users.isEmpty {
                    SGSectionTitle(text: "사용자")
                    ForEach(results.users, id: \.id) { user in
                        userRow(user)
                    }
                }
                if !results.groups.isEmpty {
                    SGSectionTitle(text: "그룹")
                    ForEach(results.groups, id: \.id) { group in
                        groupRow(group)
                    }
                }
                if !results.posts.isEmpty {
                    SGSectionTitle(text: "게시글")
                    ForEach(results.posts, id: \.id) { post in
                        postRow(post)
                    }
                }
                if !results.files.isEmpty {
                    SGSectionTitle(text: "파일")
                    ForEach(results.files, id: \.id) { file in
                        fileRow(file)
                    }
                }
                if !results.messages.isEmpty {
                    SGSectionTitle(text: "메시지")
                    ForEach(results.messages, id: \.id) { message in
                        messageRow(message)
                    }
                }
            }
            .padding(16)
        }
    }

    /// 사용자 행 — 친구 탭 SearchResultRow 미러(행 탭=공개 프로필, 친구 추가/해제는 내부 버튼)
    private func userRow(_ user: UserSearchResult) -> some View {
        Button(action: { selectedUserId = user.id }) {
            SGCard {
                HStack(spacing: 12) {
                    SGAvatar(name: user.name, imageUrl: user.profileImg)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(user.name)
                            .font(.subheadline.weight(.semibold))
                            .foregroundColor(colors.ink)
                        if let status = user.statusMessage, !status.isEmpty {
                            Text(status)
                                .font(.footnote)
                                .foregroundColor(colors.inkFaint)
                        }
                    }
                    Spacer()
                    if searchViewModel.uiState.processingUserId == user.id {
                        ProgressView().tint(colors.accent)
                    } else if searchViewModel.uiState.friendIds.contains(user.id) {
                        Button(action: { searchViewModel.onAction(.removeFriend(userId: user.id)) }) {
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
                        .disabled(searchViewModel.uiState.processingUserId != nil)
                    } else {
                        Button(action: { searchViewModel.onAction(.addFriend(user: user)) }) {
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
                        .disabled(searchViewModel.uiState.processingUserId != nil)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
        }
        .buttonStyle(.plain)
    }

    private func groupRow(_ group: GroupSearchHit) -> some View {
        Button(action: { selectedGroupId = group.id }) {
            SGCard {
                HStack(spacing: 12) {
                    SGAvatar(name: group.name, imageUrl: group.image)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(group.name)
                            .font(.subheadline.weight(.semibold))
                            .foregroundColor(colors.ink)
                        // Kotlin description은 NSObject 충돌로 description_
                        if let description = group.description_, !description.isEmpty {
                            Text(description)
                                .font(.footnote)
                                .foregroundColor(colors.inkFaint)
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

    private func postRow(_ post: PostSearchHit) -> some View {
        Button(action: { selectedPost = PostRef(groupId: post.groupId, postId: post.id) }) {
            SGCard {
                VStack(alignment: .leading, spacing: 4) {
                    Text(post.text)
                        .font(.subheadline)
                        .foregroundColor(colors.ink)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                    Text("\(post.groupName) · \(post.authorName) · \(TimeFormats.relative(post.createdAt))")
                        .font(.footnote)
                        .foregroundColor(colors.inkFaint)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
        }
        .buttonStyle(.plain)
    }

    /// 파일 행 — KMP에 그룹 파일 화면이 없어 탭하면 시스템 브라우저로 연다(스펙 확정)
    private func fileRow(_ file: FileSearchHit) -> some View {
        Button(action: {
            if let url = URL(string: file.url) { openURL(url) }
        }) {
            SGCard {
                VStack(alignment: .leading, spacing: 4) {
                    Text(file.name)
                        .font(.subheadline.weight(.semibold))
                        .foregroundColor(colors.ink)
                        .lineLimit(1)
                    Text("\(file.groupName) · \(TimeFormats.relative(file.createdAt))")
                        .font(.footnote)
                        .foregroundColor(colors.inkFaint)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
        }
        .buttonStyle(.plain)
    }

    private func messageRow(_ message: MessageSearchHit) -> some View {
        Button(action: {
            // DM은 방 이름이 없어 작성자 이름 폴백(알려진 한계 — 스펙 참조)
            selectedChatRoom = ChatRoomRef(
                chatRoomId: message.chatRoomId,
                groupId: message.groupId?.int64Value,
                title: message.groupName ?? message.authorName
            )
        }) {
            SGCard {
                VStack(alignment: .leading, spacing: 4) {
                    Text(message.text)
                        .font(.subheadline)
                        .foregroundColor(colors.ink)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                    Text("\(message.authorName) · \(message.groupName ?? "DM") · \(TimeFormats.relative(message.createdAt))")
                        .font(.footnote)
                        .foregroundColor(colors.inkFaint)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder private var groupDetailDestination: some View {
        if let groupId = selectedGroupId {
            GroupDetailView(
                groupId: groupId,
                container: container,
                chatViewModel: chatViewModel,
                theme: theme,
                profileViewModel: profileViewModel,
                onGroupClosed: {
                    selectedGroupId = nil
                    onGroupsRefreshNeeded()
                },
                onGroupUpdated: { onGroupsRefreshNeeded() }
            )
        }
    }

    @ViewBuilder private var postDetailDestination: some View {
        if let post = selectedPost {
            PostDetailView(
                container: container,
                groupId: post.groupId,
                postId: post.postId,
                chatViewModel: chatViewModel,
                profileViewModel: profileViewModel
            )
        }
    }

    @ViewBuilder private var chatRoomDestination: some View {
        if let room = selectedChatRoom {
            ChatRoomView(
                chatRoomId: room.chatRoomId,
                groupId: room.groupId,
                title: room.title,
                container: container,
                chatViewModel: chatViewModel
            )
        }
    }

    @ViewBuilder private var userProfileDestination: some View {
        if let userId = selectedUserId {
            UserProfileView(
                userId: userId,
                container: container,
                onOpenChatRoom: { room in
                    profileFollowUp = .chatRoom(room)
                    selectedUserId = nil
                },
                onOpenAccountSettings: {
                    profileFollowUp = .accountSettings
                    selectedUserId = nil
                }
            )
        }
    }

    /// 세션 ProfileViewModel을 넘겨 저장 성공 시 셸 헤더가 갱신되게 한다(MainShellView 선례)
    private var accountSettingsDestination: some View {
        AccountSettingsView(container: container, profileViewModel: profileViewModel)
    }

    /// 프로필 시트 dismiss 완료 후 후속 push 실행 — 드래그로 닫으면 followUp이 nil이라 아무 일 없다
    private func runProfileFollowUp() {
        switch profileFollowUp {
        case .chatRoom(let room): selectedChatRoom = room
        case .accountSettings: showAccountSettings = true
        case nil: break
        }
        profileFollowUp = nil
    }

    /// pop(백 버튼/스와이프) 시 상태를 nil로 되돌리는 브리지(MainShellView 선례)
    private var showGroupDetail: Binding<Bool> {
        Binding(
            get: { selectedGroupId != nil },
            set: { if !$0 { selectedGroupId = nil } }
        )
    }

    private var showPostDetail: Binding<Bool> {
        Binding(
            get: { selectedPost != nil },
            set: { if !$0 { selectedPost = nil } }
        )
    }

    private var showChatRoom: Binding<Bool> {
        Binding(
            get: { selectedChatRoom != nil },
            set: { if !$0 { selectedChatRoom = nil } }
        )
    }

    private var showUserProfile: Binding<Bool> {
        Binding(
            get: { selectedUserId != nil },
            set: { if !$0 { selectedUserId = nil } }
        )
    }

    init(
        container: AppContainer,
        chatViewModel: ChatViewModel,
        theme: SGThemeState,
        profileViewModel: ProfileViewModel,
        onGroupsRefreshNeeded: @escaping () -> Void
    ) {
        _searchViewModel = StateObject(wrappedValue: SearchViewModel(
            searchUseCase: container.searchUseCase,
            getFriendsUseCase: container.getFriendsUseCase,
            addFriendUseCase: container.addFriendUseCase,
            removeFriendUseCase: container.removeFriendUseCase
        ))
        self.container = container
        self.chatViewModel = chatViewModel
        self.theme = theme
        self.profileViewModel = profileViewModel
        self.onGroupsRefreshNeeded = onGroupsRefreshNeeded
    }
}
