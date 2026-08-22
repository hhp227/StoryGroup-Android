import Combine
import Foundation
import Shared

/// 계정 설정 — composeApp AccountSettingsViewModel.kt와 1:1 미러.
/// 웹 설정>프로필(/settings/profile)+비밀번호(/settings/password) 미러.
/// 폼 초기값용 프로필은 스스로 로드한다(push 진입마다 생성 — 최신값).
/// 이미지 변경은 선택 즉시 업로드 후 곧바로 저장까지 한다(applyProfileImage) — 프로필 미로드/저장
/// 실패 시에만 pendingProfileImg로 남겨 저장 버튼이 함께 전송한다
/// (변경 없으면 기존 profileImg를 그대로 보내 유지 — PATCH 전체 교체 계약).
/// 성공은 Event 일회성 발화 — 화면이 안내 문구와 세션 ProfileViewModel 갱신을 처리한다.
final class AccountSettingsViewModel: MviViewModel {
    @Published private(set) var uiState = UiState()

    let event = PassthroughSubject<Event, Never>()

    private let getMyProfileUseCase: GetMyProfileUseCase

    private let updateMyProfileUseCase: UpdateMyProfileUseCase

    private let changePasswordUseCase: ChangePasswordUseCase

    private let uploadImageUseCase: UploadImageUseCase

    func onAction(_ action: Action) {
        switch action {
        case .load:
            load()
        case .saveProfile(let name, let bio, let statusMessage):
            saveProfile(name: name, bio: bio, statusMessage: statusMessage)
        case .changePassword(let currentPassword, let newPassword, let confirmPassword):
            changePassword(currentPassword: currentPassword, newPassword: newPassword, confirmPassword: confirmPassword)
        case .changeProfileImage(let data, let fileName, let contentType):
            changeProfileImage(data: data, fileName: fileName, contentType: contentType)
        }
    }

    private func load() {
        if uiState.isLoading { return }

        uiState.isLoading = true
        uiState.loadError = nil
        Task { @MainActor in
            do {
                let profile = try await getMyProfileUseCase.invoke()
                uiState.isLoading = false
                uiState.profile = profile
            } catch {
                uiState.isLoading = false
                uiState.loadError = error.kotlinMessage(fallback: "내 정보를 불러오지 못했습니다.")
            }
        }
    }

    private func saveProfile(name: String, bio: String, statusMessage: String) {
        if uiState.isSaving { return }

        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmedName.isEmpty {
            uiState.saveError = "이름을 입력해주세요."
            return
        }
        uiState.isSaving = true
        uiState.saveError = nil
        Task { @MainActor in
            do {
                // 변경 이미지가 없으면 기존 값 그대로 — 빈 값은 nil로(웹 폼 bio || null 미러)
                let profile = try await updateMyProfileUseCase.invoke(
                    name: trimmedName,
                    profileImg: uiState.pendingProfileImg ?? uiState.profile?.profileImg,
                    bio: bio.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : bio,
                    statusMessage: statusMessage.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : statusMessage
                )
                uiState.isSaving = false
                uiState.profile = profile
                uiState.pendingProfileImg = nil
                event.send(.profileSaved)
            } catch {
                uiState.isSaving = false
                uiState.saveError = error.kotlinMessage(fallback: "저장에 실패했습니다.")
            }
        }
    }

    private func changeProfileImage(data: Data, fileName: String, contentType: String) {
        if uiState.isUploadingImage { return }

        uiState.isUploadingImage = true
        uiState.saveError = nil
        Task { @MainActor in
            do {
                let url = try await uploadImageUseCase.invoke(
                    bytes: data.toKotlinByteArray(),
                    fileName: fileName,
                    contentType: contentType
                )
                await applyProfileImage(url)
            } catch {
                uiState.isUploadingImage = false
                uiState.saveError = error.kotlinMessage(fallback: "이미지 업로드에 실패했습니다.")
            }
        }
    }

    /// 업로드된 이미지를 선택 즉시 저장 — 업로드만 하고 저장 버튼을 안 누르면 이미지가 조용히
    /// 유실되던 문제 방지(2026-08-21 업로드 3건·PATCH 0건 로그로 확인된 실사고).
    /// 폼의 미저장 name/bio를 함께 저장해버리지 않도록 서버 프로필 값으로 보낸다.
    /// 프로필 미로드면 종전대로 pending 보관(저장 버튼이 함께 전송) — Compose applyProfileImage 미러
    @MainActor private func applyProfileImage(_ url: String) async {
        guard let profile = uiState.profile else {
            uiState.isUploadingImage = false
            uiState.pendingProfileImg = url
            return
        }
        do {
            let updated = try await updateMyProfileUseCase.invoke(
                name: profile.name,
                profileImg: url,
                bio: profile.bio,
                statusMessage: profile.statusMessage
            )
            uiState.isUploadingImage = false
            uiState.profile = updated
            uiState.pendingProfileImg = nil
            event.send(.profileSaved)
        } catch {
            // 저장 실패여도 업로드는 살아 있다 — pending으로 남겨 저장 버튼 재시도 경로를 유지한다
            uiState.isUploadingImage = false
            uiState.pendingProfileImg = url
            uiState.saveError = error.kotlinMessage(fallback: "이미지 저장에 실패했습니다.")
        }
    }

    private func changePassword(currentPassword: String, newPassword: String, confirmPassword: String) {
        if uiState.isChangingPassword { return }

        // 클라 검증은 웹 폼 미러 — 서버(8..72, 현재 비밀번호 대조)가 최종 검사한다
        let validationError: String?
        if currentPassword.isEmpty || newPassword.isEmpty {
            validationError = "비밀번호를 입력해주세요."
        } else if newPassword.count < 8 {
            validationError = "새 비밀번호는 8자 이상이어야 합니다."
        } else if newPassword != confirmPassword {
            validationError = "새 비밀번호가 서로 일치하지 않습니다."
        } else {
            validationError = nil
        }
        if let validationError {
            uiState.passwordError = validationError
            return
        }
        uiState.isChangingPassword = true
        uiState.passwordError = nil
        Task { @MainActor in
            do {
                try await changePasswordUseCase.invoke(currentPassword: currentPassword, newPassword: newPassword)
                uiState.isChangingPassword = false
                event.send(.passwordChanged)
            } catch {
                uiState.isChangingPassword = false
                uiState.passwordError = error.kotlinMessage(fallback: "비밀번호 변경에 실패했습니다.")
            }
        }
    }

    init(
        getMyProfileUseCase: GetMyProfileUseCase,
        updateMyProfileUseCase: UpdateMyProfileUseCase,
        changePasswordUseCase: ChangePasswordUseCase,
        uploadImageUseCase: UploadImageUseCase
    ) {
        self.getMyProfileUseCase = getMyProfileUseCase
        self.updateMyProfileUseCase = updateMyProfileUseCase
        self.changePasswordUseCase = changePasswordUseCase
        self.uploadImageUseCase = uploadImageUseCase
        load()
    }

    struct UiState {
        // 폼 초기값 주입용 — 로드 전 nil이면 화면은 로딩/에러만 그린다
        var profile: Profile? = nil
        var isLoading = false
        var loadError: String? = nil
        var isSaving = false
        var saveError: String? = nil
        var isChangingPassword = false
        var passwordError: String? = nil
        // 업로드는 됐지만 아직 저장 전인 이미지 URL — 화면 아바타는 이 값을 우선 표시
        var pendingProfileImg: String? = nil
        var isUploadingImage = false

        var displayedProfileImg: String? { pendingProfileImg ?? profile?.profileImg }
    }

    enum Action {
        case load
        case saveProfile(name: String, bio: String, statusMessage: String)
        case changePassword(currentPassword: String, newPassword: String, confirmPassword: String)
        case changeProfileImage(data: Data, fileName: String, contentType: String)
    }

    enum Event {
        case profileSaved
        case passwordChanged
    }
}
