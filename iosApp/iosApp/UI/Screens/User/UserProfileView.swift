import SwiftUI
import Shared

/// 공개 프로필 — composeApp UserProfileScreen.kt와 1:1 미러(웹 /users/[userId]).
/// 자체 push 2종: DM 성공 → 채팅방, 본인 "프로필 수정" → 계정 설정(GroupDetailView 자체 push 선례).
struct UserProfileView: View {
    let container: AppContainer

    /// 채팅방 push에 필요 — 셸 소유 세션 VM 전달(pass-through라 plain let, GroupDetailView 선례)
    let chatViewModel: ChatViewModel

    /// 계정 설정 push에 필요 — 셸 소유 세션 VM(저장 성공 시 셸 헤더 갱신 공유)
    let profileViewModel: ProfileViewModel

    @StateObject private var userProfileViewModel: UserProfileViewModel

    @State private var selectedChatRoom: ChatRoomRef? = nil

    @State private var showAccountSettings = false

    @Environment(\.sgColors) private var colors

    var body: some View {
        pushContainer
            .navigationTitle("프로필")
            .navigationBarTitleDisplayMode(.inline)
            .onReceive(userProfileViewModel.event) { event in
                switch event {
                case .dmOpened(let chatRoomId, let title):
                    selectedChatRoom = ChatRoomRef(chatRoomId: chatRoomId, groupId: nil, title: title)
                }
            }
    }

    /// 자체 push 2종 — iOS 16 navigationDestination / iOS 15 숨김 NavigationLink 폴백(GroupDetailView 선례)
    @ViewBuilder private var pushContainer: some View {
        if #available(iOS 16.0, *) {
            content
                .navigationDestination(isPresented: showChatRoom) { chatRoomDestination }
                .navigationDestination(isPresented: $showAccountSettings) { accountSettingsDestination }
        } else {
            content
                .background(
                    NavigationLink(isActive: showChatRoom) { chatRoomDestination } label: { EmptyView() }.hidden()
                )
                .background(
                    NavigationLink(isActive: $showAccountSettings) { accountSettingsDestination } label: { EmptyView() }.hidden()
                )
        }
    }

    @ViewBuilder private var content: some View {
        let uiState = userProfileViewModel.uiState

        if uiState.profile == nil && uiState.isLoading {
            ProgressView()
                .tint(colors.accent)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(colors.paper.ignoresSafeArea())
        } else if let profile = uiState.profile {
            profileBody(profile, uiState: uiState)
        } else {
            VStack(spacing: 8) {
                Text(uiState.loadError ?? "프로필을 불러오지 못했습니다.")
                    .font(.subheadline)
                    .foregroundColor(colors.rust)
                Button("다시 시도") { userProfileViewModel.onAction(.refresh) }
                    .font(.subheadline.bold())
                    .foregroundColor(colors.accent)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(colors.paper.ignoresSafeArea())
        }
    }

    private func profileBody(_ profile: PublicProfile, uiState: UserProfileViewModel.UiState) -> some View {
        ScrollView {
            SGCard {
                VStack(alignment: .leading, spacing: 16) {
                    HStack(spacing: 16) {
                        SGAvatar(name: profile.name, size: 72, imageUrl: profile.profileImg)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(profile.name)
                                .font(.title3.bold())
                                .foregroundColor(colors.ink)
                            if let statusMessage = profile.statusMessage, !statusMessage.isEmpty {
                                Text(statusMessage)
                                    .font(.footnote)
                                    .foregroundColor(colors.inkSoft)
                            }
                            Text("\(TimeFormats.joinDate(profile.createdAt)) 가입")
                                .font(.caption2)
                                .foregroundColor(colors.inkFaint)
                        }
                    }
                    if let bio = profile.bio, !bio.isEmpty {
                        Divider().background(colors.stoneBorder)
                        Text(bio)
                            .font(.subheadline)
                            .foregroundColor(colors.ink)
                    }
                    HStack(spacing: 8) {
                        if uiState.isSelf {
                            Button("프로필 수정") { showAccountSettings = true }
                                .font(.subheadline.bold())
                                .foregroundColor(colors.inkSoft)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 6)
                                .background(
                                    RoundedRectangle(cornerRadius: colors.radiusButton ?? 12, style: .continuous)
                                        .stroke(colors.stoneBorder, lineWidth: 1)
                                )
                                .buttonStyle(.plain)
                        } else {
                            Button("1:1 DM") { userProfileViewModel.onAction(.openDm) }
                                .font(.subheadline.bold())
                                .foregroundColor(colors.onAccent)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 6)
                                .background(
                                    RoundedRectangle(cornerRadius: colors.radiusButton ?? 12, style: .continuous)
                                        .fill(colors.accent)
                                )
                                .buttonStyle(.plain)
                                .disabled(uiState.isOpeningDm)
                            // 친구 여부 판정 불가(목록 로드 실패)면 버튼을 숨긴다 — 웹 isFriend===null 미러
                            if let isFriend = uiState.isFriend {
                                Button(isFriend ? "친구 해제" : "친구 추가") { userProfileViewModel.onAction(.toggleFriend) }
                                    .font(.subheadline.bold())
                                    .foregroundColor(colors.inkSoft)
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 6)
                                    .background(
                                        RoundedRectangle(cornerRadius: colors.radiusButton ?? 12, style: .continuous)
                                            .stroke(colors.stoneBorder, lineWidth: 1)
                                    )
                                    .buttonStyle(.plain)
                                    .disabled(uiState.isBusy)
                            }
                        }
                    }
                    if let actionError = uiState.actionError {
                        Text(actionError)
                            .font(.footnote)
                            .foregroundColor(colors.rust)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(20)
            }
            .padding(16)
        }
        .background(colors.paper.ignoresSafeArea())
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

    private var accountSettingsDestination: some View {
        AccountSettingsView(container: container, profileViewModel: profileViewModel)
    }

    /// pop(백 버튼/스와이프) 시 상태를 nil로 되돌리는 브리지(MainShellView 선례)
    private var showChatRoom: Binding<Bool> {
        Binding(
            get: { selectedChatRoom != nil },
            set: { if !$0 { selectedChatRoom = nil } }
        )
    }

    init(userId: Int64, container: AppContainer, chatViewModel: ChatViewModel, profileViewModel: ProfileViewModel) {
        _userProfileViewModel = StateObject(wrappedValue: UserProfileViewModel(
            userId: userId,
            getPublicProfileUseCase: container.getPublicProfileUseCase,
            getFriendsUseCase: container.getFriendsUseCase,
            addFriendUseCase: container.addFriendUseCase,
            removeFriendUseCase: container.removeFriendUseCase,
            openDirectRoomUseCase: container.openDirectRoomUseCase,
            getCurrentUserIdUseCase: container.getCurrentUserIdUseCase
        ))
        self.container = container
        self.chatViewModel = chatViewModel
        self.profileViewModel = profileViewModel
    }
}
