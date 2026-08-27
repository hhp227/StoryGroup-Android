import SwiftUI
import Shared

/// 차단 사용자 관리 — composeApp BlockedUsersScreen.kt와 1:1 미러(웹 /settings/blocked:
/// 아바타+이름+차단일+해제 버튼). 진입점은 셸 프로필 탭 메뉴(MainShellView push).
struct BlockedUsersView: View {
    @StateObject private var blockedUsersViewModel = BlockedUsersViewModel()

    @Environment(\.sgColors) private var colors

    var body: some View {
        content
            .navigationTitle("차단 사용자 관리")
            .navigationBarTitleDisplayMode(.inline)
            // 호출 화면이 투명 바(커버 펼침) 상태로 push해도 이 화면은 기본 내비바 — 복귀 시엔 호출 화면이 재적용
            .navigationBarScrim(visible: true)
    }

    @ViewBuilder private var content: some View {
        let uiState = blockedUsersViewModel.uiState

        if uiState.blocked == nil && uiState.isLoading {
            ProgressView()
                .tint(colors.accent)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(colors.paper.ignoresSafeArea())
        } else if let blocked = uiState.blocked {
            if blocked.isEmpty {
                VStack(spacing: 8) {
                    Text("차단한 사용자가 없습니다.")
                        .font(.subheadline.bold())
                        .foregroundColor(colors.ink)
                    Text("차단하면 그 사용자의 게시글·댓글·채팅이 내 화면에서 숨겨지고, 서로 DM을 보낼 수 없습니다.")
                        .font(.footnote)
                        .foregroundColor(colors.inkFaint)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 32)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(colors.paper.ignoresSafeArea())
            } else {
                ScrollView {
                    VStack(spacing: 8) {
                        if let actionError = uiState.actionError {
                            Text(actionError)
                                .font(.footnote)
                                .foregroundColor(colors.rust)
                        }
                        ForEach(blocked, id: \.userId) { user in
                            blockedRow(user, isBusy: uiState.busyUserId != nil)
                        }
                    }
                    .padding(16)
                }
                .background(colors.paper.ignoresSafeArea())
            }
        } else {
            VStack(spacing: 8) {
                Text(uiState.loadError ?? "차단 목록을 불러오지 못했습니다.")
                    .font(.subheadline)
                    .foregroundColor(colors.rust)
                Button("다시 시도") { blockedUsersViewModel.onAction(.refresh) }
                    .font(.subheadline.bold())
                    .foregroundColor(colors.accent)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(colors.paper.ignoresSafeArea())
        }
    }

    private func blockedRow(_ user: BlockedUser, isBusy: Bool) -> some View {
        SGCard {
            HStack(spacing: 12) {
                SGAvatar(name: user.name, size: 44, imageUrl: user.profileImg)
                VStack(alignment: .leading, spacing: 2) {
                    Text(user.name)
                        .font(.subheadline.bold())
                        .foregroundColor(colors.ink)
                    Text("\(TimeFormats.joinDate(user.blockedAt)) 차단")
                        .font(.caption2)
                        .foregroundColor(colors.inkFaint)
                }
                Spacer()
                Button("차단 해제") { blockedUsersViewModel.onAction(.unblock(user.userId)) }
                    .font(.subheadline.bold())
                    .foregroundColor(colors.inkSoft)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(
                        RoundedRectangle(cornerRadius: colors.radiusButton ?? 12, style: .continuous)
                            .stroke(colors.stoneBorder, lineWidth: 1)
                    )
                    .buttonStyle(.plain)
                    .disabled(isBusy)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
    }
}
