import SwiftUI
import Shared

/// 계정 설정 — Compose AccountSettingsScreen 미러(프로필 수정+비밀번호 변경 두 카드,
/// 웹 /settings/profile·password 두 페이지를 한 화면으로). 루트 NavigationStack 풀스크린 push.
/// 폼 필드는 화면 소유(CreatePost 패턴), 저장 성공 시 셸 소유 세션 ProfileViewModel을 갱신해
/// 프로필 탭/드로어 헤더에 반영한다.
struct AccountSettingsView: View {
    @StateObject private var accountSettingsViewModel = AccountSettingsViewModel()

    /// 저장 성공 반영용 — 프로필 탭/드로어 헤더와 같은 세션 인스턴스(셸 소유).
    /// onAction 호출만 하므로 관찰(@ObservedObject)은 불필요
    private let profileViewModel: ProfileViewModel

    @Environment(\.sgColors) private var colors

    @State private var name = ""

    @State private var statusMessage = ""

    @State private var bio = ""

    /// 폼 초기값은 프로필 로드 후 1회만 주입 — 이후엔 사용자 입력이 우선(재로드에 덮이지 않게)
    @State private var formFilled = false

    @State private var currentPassword = ""

    @State private var newPassword = ""

    @State private var confirmPassword = ""

    @State private var profileSaved = false

    @State private var passwordChanged = false

    @State private var showImagePicker = false

    var body: some View {
        content
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(colors.paper)
            .navigationTitle("계정 설정")
            .navigationBarTitleDisplayMode(.inline)
            // 호출 화면이 투명 바(커버 펼침) 상태로 push해도 이 화면은 기본 내비바 — 복귀 시엔 호출 화면이 재적용
            .navigationBarScrim(visible: true)
            .onReceive(accountSettingsViewModel.$uiState.map(\.profile)) { profile in
                guard let profile, !formFilled else { return }

                name = profile.name
                statusMessage = profile.statusMessage ?? ""
                bio = profile.bio ?? ""
                formFilled = true
            }
            // 일회성 이벤트 수집 — 성공 안내 문구는 웹 미러
            .onReceive(accountSettingsViewModel.event) { event in
                switch event {
                case .profileSaved:
                    profileSaved = true
                    profileViewModel.onAction(.load)
                case .passwordChanged:
                    passwordChanged = true
                    currentPassword = ""
                    newPassword = ""
                    confirmPassword = ""
                }
            }
            .sheet(isPresented: $showImagePicker) {
                ImagePicker { data, fileName, contentType in
                    accountSettingsViewModel.onAction(.changeProfileImage(data: data, fileName: fileName, contentType: contentType))
                }
            }
    }

    @ViewBuilder private var content: some View {
        let uiState = accountSettingsViewModel.uiState

        if uiState.profile == nil, uiState.isLoading {
            ProgressView().padding(.vertical, 48)
        } else if uiState.profile == nil, let loadError = uiState.loadError {
            VStack(spacing: 8) {
                Text(loadError).font(.subheadline).foregroundColor(colors.rust)
                Button("다시 시도") { accountSettingsViewModel.onAction(.load) }
                    .font(.subheadline)
                    .foregroundColor(colors.accent)
            }
            .padding(.vertical, 48)
        } else {
            ScrollView {
                VStack(spacing: 12) {
                    profileCard
                    passwordCard
                }
                .padding(16)
            }
        }
    }

    private var profileCard: some View {
        SGCard {
            VStack(alignment: .leading, spacing: 12) {
                Text("프로필").font(.headline).foregroundColor(colors.ink)
                // 웹 폼 상단 미러 — 아바타(탭하면 변경)+이메일(이메일은 수정 불가)
                HStack(spacing: 12) {
                    ZStack {
                        SGAvatar(name: name.isEmpty ? "?" : name, size: 48, imageUrl: accountSettingsViewModel.uiState.displayedProfileImg)
                        if accountSettingsViewModel.uiState.isUploadingImage {
                            Circle().fill(colors.ink.opacity(0.4)).frame(width: 48, height: 48)
                            ProgressView().tint(colors.onAccent)
                        } else {
                            Image(systemName: "camera.fill")
                                .font(.system(size: 10))
                                .foregroundColor(colors.onAccent)
                                .padding(3)
                                .background(Circle().fill(colors.ink))
                                .offset(x: 16, y: 16)
                        }
                    }
                    .contentShape(Circle())
                    .onTapGesture {
                        if !accountSettingsViewModel.uiState.isUploadingImage { showImagePicker = true }
                    }
                    Text(accountSettingsViewModel.uiState.profile?.email ?? "")
                        .font(.caption)
                        .foregroundColor(colors.inkFaint)
                }
                SGTextField(label: "이름", text: $name, enabled: !accountSettingsViewModel.uiState.isSaving)
                    .onChange(of: name) { if $0.count > 50 { name = String($0.prefix(50)) } }
                SGTextField(label: "상태메시지", text: $statusMessage, enabled: !accountSettingsViewModel.uiState.isSaving)
                    .onChange(of: statusMessage) { if $0.count > 100 { statusMessage = String($0.prefix(100)) } }
                VStack(alignment: .leading, spacing: 6) {
                    Text("소개").font(.caption.bold()).foregroundColor(colors.inkSoft)
                    // 멀티라인은 TextEditor(웹 textarea 미러) — linen 채움은 iOS 15에서
                    // TextEditor 배경 제어가 안 돼 보더만 두른다(CreatePostView와 동일한 타협)
                    TextEditor(text: $bio)
                        .frame(minHeight: 96)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(RoundedRectangle(cornerRadius: 10).stroke(colors.stoneBorder, lineWidth: 1))
                        .disabled(accountSettingsViewModel.uiState.isSaving)
                        .onChange(of: bio) { if $0.count > 500 { bio = String($0.prefix(500)) } }
                }
                if let saveError = accountSettingsViewModel.uiState.saveError {
                    Text(saveError).font(.caption).foregroundColor(colors.rust)
                }
                if profileSaved {
                    Text("저장했습니다.").font(.caption).foregroundColor(colors.accent)
                }
                SGPrimaryButton(title: "저장", isLoading: accountSettingsViewModel.uiState.isSaving) {
                    profileSaved = false
                    accountSettingsViewModel.onAction(.saveProfile(name: name, bio: bio, statusMessage: statusMessage))
                }
            }
            .padding(16)
        }
    }

    private var passwordCard: some View {
        SGCard {
            VStack(alignment: .leading, spacing: 12) {
                Text("비밀번호 변경").font(.headline).foregroundColor(colors.ink)
                SGTextField(
                    label: "현재 비밀번호",
                    text: $currentPassword,
                    isSecure: true,
                    enabled: !accountSettingsViewModel.uiState.isChangingPassword
                )
                SGTextField(
                    label: "새 비밀번호 (8자 이상)",
                    text: $newPassword,
                    isSecure: true,
                    enabled: !accountSettingsViewModel.uiState.isChangingPassword
                )
                SGTextField(
                    label: "새 비밀번호 확인",
                    text: $confirmPassword,
                    isSecure: true,
                    enabled: !accountSettingsViewModel.uiState.isChangingPassword
                )
                if let passwordError = accountSettingsViewModel.uiState.passwordError {
                    Text(passwordError).font(.caption).foregroundColor(colors.rust)
                }
                if passwordChanged {
                    Text("비밀번호를 변경했습니다. 다른 기기에서는 다시 로그인해야 합니다.")
                        .font(.caption)
                        .foregroundColor(colors.accent)
                }
                SGPrimaryButton(title: "비밀번호 변경", isLoading: accountSettingsViewModel.uiState.isChangingPassword) {
                    passwordChanged = false
                    accountSettingsViewModel.onAction(
                        .changePassword(
                            currentPassword: currentPassword,
                            newPassword: newPassword,
                            confirmPassword: confirmPassword
                        )
                    )
                }
            }
            .padding(16)
        }
    }

    init(profileViewModel: ProfileViewModel) {
        self.profileViewModel = profileViewModel
    }
}
