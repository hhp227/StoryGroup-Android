package kr.hhp227.storygroup.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.shared.domain.model.Profile
import kr.hhp227.storygroup.shared.domain.usecase.ChangePasswordUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetMyProfileUseCase
import kr.hhp227.storygroup.shared.domain.usecase.UpdateMyProfileUseCase
import kr.hhp227.storygroup.shared.domain.usecase.UploadImageUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel

/**
 * 계정 설정 — 웹 설정>프로필(/settings/profile)+비밀번호(/settings/password) 미러.
 * 폼 초기값용 프로필은 스스로 로드한다(백스택 엔트리 스코프 — 진입마다 최신값).
 * 이미지 변경은 선택 즉시 업로드 후 곧바로 저장까지 한다(applyProfileImage) — 프로필 미로드/저장
 * 실패 시에만 pendingProfileImg로 남겨 저장 버튼이 함께 전송한다
 * (변경 없으면 기존 profileImg를 그대로 보내 유지 — PATCH 전체 교체 계약).
 * 성공은 Event 일회성 발화 — 화면이 안내 문구와 세션 ProfileViewModel 갱신을 처리한다.
 * iosApp AccountSettingsViewModel.swift와 1:1 미러
 */
class AccountSettingsViewModel(
    private val getMyProfileUseCase: GetMyProfileUseCase,
    private val updateMyProfileUseCase: UpdateMyProfileUseCase,
    private val changePasswordUseCase: ChangePasswordUseCase,
    private val uploadImageUseCase: UploadImageUseCase
) : ViewModel(), MviViewModel<AccountSettingsViewModel.UiState, AccountSettingsViewModel.Action, AccountSettingsViewModel.Event> {
    private val _uiState = MutableStateFlow(UiState())
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<Event>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val event: Flow<Event> = _event.asSharedFlow()

    override fun onAction(action: Action) {
        when (action) {
            Action.Load -> load()
            is Action.SaveProfile -> saveProfile(action.name, action.bio, action.statusMessage)
            is Action.ChangePassword ->
                changePassword(action.currentPassword, action.newPassword, action.confirmPassword)
            is Action.ChangeProfileImage -> changeProfileImage(action.bytes, action.fileName, action.contentType)
        }
    }

    private fun load() {
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            runCatching { getMyProfileUseCase() }
                .onSuccess { profile ->
                    _uiState.update { it.copy(isLoading = false, profile = profile) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, loadError = e.message ?: "내 정보를 불러오지 못했습니다.") }
                }
        }
    }

    private fun saveProfile(name: String, bio: String, statusMessage: String) {
        if (_uiState.value.isSaving) return
        if (name.isBlank()) {
            _uiState.update { it.copy(saveError = "이름을 입력해주세요.") }
            return
        }
        _uiState.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            runCatching {
                // 변경 이미지가 없으면 기존 값 그대로 — 빈 문자열은 null로(웹 폼 profileImg || null 미러)
                updateMyProfileUseCase(
                    name = name.trim(),
                    profileImg = _uiState.value.pendingProfileImg ?: _uiState.value.profile?.profileImg,
                    bio = bio.ifBlank { null },
                    statusMessage = statusMessage.ifBlank { null }
                )
            }.onSuccess { profile ->
                _uiState.update { it.copy(isSaving = false, profile = profile, pendingProfileImg = null) }
                _event.tryEmit(Event.ProfileSaved)
            }.onFailure { e ->
                _uiState.update { it.copy(isSaving = false, saveError = e.message ?: "저장에 실패했습니다.") }
            }
        }
    }

    private fun changeProfileImage(bytes: ByteArray, fileName: String, contentType: String) {
        if (_uiState.value.isUploadingImage) return

        _uiState.update { it.copy(isUploadingImage = true, saveError = null) }
        viewModelScope.launch {
            runCatching { uploadImageUseCase(bytes, fileName, contentType) }
                .onSuccess { url -> applyProfileImage(url) }
                .onFailure { e ->
                    _uiState.update { it.copy(isUploadingImage = false, saveError = e.message ?: "이미지 업로드에 실패했습니다.") }
                }
        }
    }

    /**
     * 업로드된 이미지를 선택 즉시 저장 — 업로드만 하고 저장 버튼을 안 누르면 이미지가 조용히
     * 유실되던 문제 방지(2026-08-21 업로드 3건·PATCH 0건 로그로 확인된 실사고).
     * 폼의 미저장 name/bio를 함께 저장해버리지 않도록 서버 프로필 값으로 보낸다.
     * 프로필 미로드면 종전대로 pending 보관(저장 버튼이 함께 전송)
     */
    private suspend fun applyProfileImage(url: String) {
        val profile = _uiState.value.profile
        if (profile == null) {
            _uiState.update { it.copy(isUploadingImage = false, pendingProfileImg = url) }
            return
        }
        runCatching {
            updateMyProfileUseCase(
                name = profile.name,
                profileImg = url,
                bio = profile.bio,
                statusMessage = profile.statusMessage
            )
        }.onSuccess { updated ->
            _uiState.update { it.copy(isUploadingImage = false, profile = updated, pendingProfileImg = null) }
            _event.tryEmit(Event.ProfileSaved)
        }.onFailure { e ->
            // 저장 실패여도 업로드는 살아 있다 — pending으로 남겨 저장 버튼 재시도 경로를 유지한다
            _uiState.update { it.copy(isUploadingImage = false, pendingProfileImg = url, saveError = e.message ?: "이미지 저장에 실패했습니다.") }
        }
    }

    private fun changePassword(currentPassword: String, newPassword: String, confirmPassword: String) {
        if (_uiState.value.isChangingPassword) return
        // 클라 검증은 웹 폼 미러 — 서버(8..72, 현재 비밀번호 대조)가 최종 검사한다
        val validationError = when {
            currentPassword.isEmpty() || newPassword.isEmpty() -> "비밀번호를 입력해주세요."
            newPassword.length < 8 -> "새 비밀번호는 8자 이상이어야 합니다."
            newPassword != confirmPassword -> "새 비밀번호가 서로 일치하지 않습니다."
            else -> null
        }
        if (validationError != null) {
            _uiState.update { it.copy(passwordError = validationError) }
            return
        }
        _uiState.update { it.copy(isChangingPassword = true, passwordError = null) }
        viewModelScope.launch {
            runCatching { changePasswordUseCase(currentPassword, newPassword) }
                .onSuccess {
                    _uiState.update { it.copy(isChangingPassword = false) }
                    _event.tryEmit(Event.PasswordChanged)
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isChangingPassword = false, passwordError = e.message ?: "비밀번호 변경에 실패했습니다.") }
                }
        }
    }

    init {
        load()
    }

    data class UiState(
        // 폼 초기값 주입용 — 로드 전 null이면 화면은 로딩/에러만 그린다
        val profile: Profile? = null,
        val isLoading: Boolean = false,
        val loadError: String? = null,
        val isSaving: Boolean = false,
        val saveError: String? = null,
        val isChangingPassword: Boolean = false,
        val passwordError: String? = null,
        // 업로드는 됐지만 아직 저장 전인 이미지 URL — 화면 아바타는 이 값을 우선 표시
        val pendingProfileImg: String? = null,
        val isUploadingImage: Boolean = false
    ) {
        val displayedProfileImg: String? get() = pendingProfileImg ?: profile?.profileImg
    }

    sealed interface Action {
        data object Load : Action
        data class SaveProfile(val name: String, val bio: String, val statusMessage: String) : Action
        data class ChangePassword(
            val currentPassword: String,
            val newPassword: String,
            val confirmPassword: String
        ) : Action
        class ChangeProfileImage(val bytes: ByteArray, val fileName: String, val contentType: String) : Action
    }

    sealed interface Event {
        data object ProfileSaved : Event
        data object PasswordChanged : Event
    }
}
