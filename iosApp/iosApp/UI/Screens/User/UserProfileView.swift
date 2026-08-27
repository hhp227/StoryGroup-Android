import SwiftUI
import Shared

/// 공개 프로필 — composeApp UserProfileScreen.kt와 1:1 미러(웹 /users/[userId]).
/// push가 아니라 시트로 뜬다(Compose는 카드 다이얼로그). 후속 이동 2종(DM 성공→채팅방,
/// 본인 "프로필 수정"→계정 설정)은 콜백으로 부모에 넘기고, 부모가 시트를 닫은 뒤 push한다
struct UserProfileView: View {
    /// DM 성공 — 부모가 시트를 닫고(onDismiss 완료 후) 채팅방을 push한다
    let onOpenChatRoom: (ChatRoomRef) -> Void

    /// 본인 "프로필 수정" — 부모가 시트를 닫고 계정 설정을 push한다
    let onOpenAccountSettings: () -> Void

    @StateObject private var userProfileViewModel: UserProfileViewModel

    @Environment(\.sgColors) private var colors

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: 0) {
            header
            content
        }
        .background(colors.paper.ignoresSafeArea())
        .onReceive(userProfileViewModel.event) { event in
            switch event {
            case .dmOpened(let chatRoomId, let title):
                onOpenChatRoom(ChatRoomRef(chatRoomId: chatRoomId, groupId: nil, title: title))
            }
        }
    }

    /// 시트라 내비바가 없다 — 제목+닫기(X)를 직접 그린다
    private var header: some View {
        HStack {
            Text("프로필")
                .font(.headline)
                .foregroundColor(colors.ink)
            Spacer()
            Button { dismiss() } label: {
                Image(systemName: "xmark")
                    .font(.subheadline.bold())
                    .foregroundColor(colors.inkSoft)
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 20)
        .padding(.top, 20)
        .padding(.bottom, 4)
    }

    @ViewBuilder private var content: some View {
        let uiState = userProfileViewModel.uiState

        if uiState.profile == nil && uiState.isLoading {
            ProgressView()
                .tint(colors.accent)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
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
                            Button("프로필 수정") { onOpenAccountSettings() }
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
    }

    init(
        userId: Int64,
        onOpenChatRoom: @escaping (ChatRoomRef) -> Void,
        onOpenAccountSettings: @escaping () -> Void
    ) {
        _userProfileViewModel = StateObject(wrappedValue: UserProfileViewModel(userId: userId))
        self.onOpenChatRoom = onOpenChatRoom
        self.onOpenAccountSettings = onOpenAccountSettings
    }
}
