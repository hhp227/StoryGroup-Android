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

    /// 탈퇴 성공 콜백 — 호출부가 기존 로그아웃 경로(AppRootView.swift의 MainShellView onLogout:
    /// PushRegistrar.unregisterCurrentToken()+LoginViewModel.Action.logout)로 이어 붙인다.
    /// Compose AccountSettingsScreen의 onAccountDeleted 파라미터 미러. 이 화면을 push하는
    /// 4개 호출부(MainShellView 셸 진입점, GroupDetailView/PostDetailView/SearchView의 "본인
    /// 프로필→계정 설정" 체인) 전부가 각자의 onLogout 또는 그 릴레이를 명시적으로 채운다 —
    /// 기본값을 두지 않아 새 호출부가 생기면 컴파일이 깨져 배선 누락을 강제로 잡는다
    private let onAccountDeleted: () -> Void

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

    /// 탈퇴 확인 다이얼로그 — 레거시 그룹 삭제/나가기 확인 다이얼로그 관용구 미러
    @State private var confirmingDelete = false

    @State private var deletePassword = ""

    /// 이번에 다이얼로그를 연 뒤 실제로 제출한 적이 있을 때만 VM 에러를 보여준다 — 취소 후 재오픈 시
    /// 직전 실패 문구(예: "현재 비밀번호가 올바르지 않습니다")가 입력 전인데 먼저 보이는 문제 방지.
    /// VM은 화면(push)보다 오래 살 수 있어 에러가 자연 소멸하지 않으므로 화면 로컬로 게이트.
    @State private var deleteAttempted = false

    var body: some View {
        content
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(colors.paper)
            .overlay {
                if confirmingDelete {
                    DeleteAccountDialog(
                        password: $deletePassword,
                        isLoading: accountSettingsViewModel.uiState.isDeletingAccount,
                        // 이번 오픈에서 한 번이라도 제출했을 때만 VM 에러를 노출(위 deleteAttempted 주석 참고)
                        error: deleteAttempted ? accountSettingsViewModel.uiState.deleteAccountError : nil,
                        onDismiss: {
                            confirmingDelete = false
                            deletePassword = ""
                            deleteAttempted = false
                        },
                        onConfirm: {
                            deleteAttempted = true
                            accountSettingsViewModel.onAction(.deleteAccount(password: deletePassword))
                        }
                    )
                }
            }
            .navigationTitle("계정 설정")
            .navigationBarTitleDisplayMode(.inline)
            // 삭제 인플라이트 중 back으로 pop되면 서버는 탈퇴됐는데 .onReceive(event)가 사라져
            // accountDeleted를 못 받아 로그아웃이 안 걸린다(좀비 세션) — 백 버튼만 차단.
            // ⚠️ 스와이프백(엣지 팬 제스처)은 이 modifier로 안 막힌다 — Mac에서 실기기로 별도 확인 필요
            .navigationBarBackButtonHidden(accountSettingsViewModel.uiState.isDeletingAccount)
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
                case .accountDeleted:
                    confirmingDelete = false
                    deletePassword = ""
                    deleteAttempted = false
                    onAccountDeleted()
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
                    deleteAccountRow
                }
                .padding(16)
            }
        }
    }

    /// 설정 목록 끝 — 회원 탈퇴(위험색 행, 탭하면 확인 다이얼로그)
    private var deleteAccountRow: some View {
        SGCard {
            HStack {
                Text("회원 탈퇴").font(.body).foregroundColor(colors.rust)
                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .contentShape(Rectangle())
            .onTapGesture {
                guard !accountSettingsViewModel.uiState.isDeletingAccount else { return }
                // 새로 여는 참이니 직전(취소된) 시도의 잔존 에러는 숨긴다
                deleteAttempted = false
                confirmingDelete = true
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

    init(profileViewModel: ProfileViewModel, onAccountDeleted: @escaping () -> Void) {
        self.profileViewModel = profileViewModel
        self.onAccountDeleted = onAccountDeleted
    }
}

/// 회원 탈퇴 확인 다이얼로그 — PostDetailView의 ActionConfirmDialog(신고·차단) 관용구 미러
/// + 본인 확인용 비밀번호 필드. 성공하면 accountDeleted 이벤트로 화면이 닫고 로그아웃 흐름으로 넘어간다.
private struct DeleteAccountDialog: View {
    @Binding var password: String

    let isLoading: Bool

    let error: String?

    let onDismiss: () -> Void

    let onConfirm: () -> Void

    @Environment(\.sgColors) private var colors

    private var canConfirm: Bool {
        !isLoading && !password.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var body: some View {
        ZStack {
            Color.black.opacity(0.35)
                .ignoresSafeArea()
                .onTapGesture(perform: onDismiss)
            SGCard {
                VStack(alignment: .leading, spacing: 12) {
                    Text("회원 탈퇴").font(.headline).foregroundColor(colors.ink)
                    Text("정말 탈퇴하시겠어요? 되돌릴 수 없으며, 작성한 게시글·댓글·채팅은 '탈퇴한 사용자'로 남습니다.")
                        .font(.subheadline)
                        .foregroundColor(colors.ink)
                    SGTextField(label: "현재 비밀번호", text: $password, isSecure: true, enabled: !isLoading)
                    if let error {
                        Text(error).font(.caption).foregroundColor(colors.rust)
                    }
                    HStack(spacing: 8) {
                        Button(action: onDismiss) {
                            Text("취소")
                                .font(.subheadline)
                                .frame(maxWidth: .infinity)
                                // SGPrimaryButton과 같은 높이로 나란히 맞춘다
                                .frame(height: 48)
                                .overlay(
                                    RoundedRectangle(cornerRadius: colors.radiusButton ?? 20, style: .continuous)
                                        .stroke(colors.stoneBorder, lineWidth: 1)
                                )
                                .foregroundColor(colors.ink)
                        }
                        .buttonStyle(.plain)
                        .disabled(isLoading)
                        Button(action: onConfirm) {
                            HStack(spacing: 8) {
                                if isLoading {
                                    ProgressView().progressViewStyle(CircularProgressViewStyle(tint: colors.inkFaint))
                                }
                                Text("탈퇴").font(.system(size: 16, weight: .bold))
                            }
                            .frame(maxWidth: .infinity)
                            .frame(height: 48)
                            .background(
                                RoundedRectangle(cornerRadius: colors.radiusButton ?? 24, style: .continuous)
                                    .fill(canConfirm ? colors.rust : colors.rust.opacity(0.4))
                            )
                            .foregroundColor(colors.onAccent)
                        }
                        .buttonStyle(.plain)
                        .disabled(!canConfirm)
                    }
                }
                .padding(16)
            }
            .padding(24)
        }
    }
}
