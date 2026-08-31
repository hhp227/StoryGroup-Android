package kr.hhp227.storygroup.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kr.hhp227.storygroup.di.screenViewModel
import kr.hhp227.storygroup.di.sessionViewModel
import kr.hhp227.storygroup.ui.components.SgAvatar
import kr.hhp227.storygroup.ui.components.SgCard
import kr.hhp227.storygroup.ui.components.SgPrimaryButton
import kr.hhp227.storygroup.ui.components.SgTextField
import kr.hhp227.storygroup.ui.components.SgTopBar
import kr.hhp227.storygroup.ui.navigation.NavigationAction
import kr.hhp227.storygroup.ui.navigation.sessionNavigationViewModel
import kr.hhp227.storygroup.ui.screens.profile.ProfileViewModel
import kr.hhp227.storygroup.ui.theme.SgTheme
import kr.hhp227.storygroup.ui.util.rememberImagePickerLauncher
import org.jetbrains.compose.resources.stringResource
import storygroup.composeapp.generated.resources.Res
import storygroup.composeapp.generated.resources.account_bio
import storygroup.composeapp.generated.resources.account_change_password
import storygroup.composeapp.generated.resources.account_change_photo
import storygroup.composeapp.generated.resources.account_confirm_password
import storygroup.composeapp.generated.resources.account_current_password
import storygroup.composeapp.generated.resources.account_delete_account
import storygroup.composeapp.generated.resources.account_delete_action
import storygroup.composeapp.generated.resources.account_delete_confirm
import storygroup.composeapp.generated.resources.account_new_password
import storygroup.composeapp.generated.resources.account_password_changed
import storygroup.composeapp.generated.resources.account_saved
import storygroup.composeapp.generated.resources.account_status_message
import storygroup.composeapp.generated.resources.auth_name
import storygroup.composeapp.generated.resources.common_back
import storygroup.composeapp.generated.resources.common_cancel
import storygroup.composeapp.generated.resources.common_profile
import storygroup.composeapp.generated.resources.common_retry
import storygroup.composeapp.generated.resources.common_save
import storygroup.composeapp.generated.resources.profile_account_settings

/**
 * 계정 설정 — 프로필 수정+비밀번호 변경(웹 /settings/profile·password 두 페이지를 한 화면 두 카드로).
 * NavHost 풀스크린 목적지라 상단바는 화면이 소유한다. 폼 필드는 화면 소유(CreatePost 패턴),
 * 저장 성공 시 세션 ProfileViewModel을 갱신해 프로필 탭/드로어 헤더에 반영한다.
 * iosApp AccountSettingsView.swift와 1:1 미러
 */
@Composable
fun AccountSettingsScreen(
    modifier: Modifier = Modifier,
    onNavigationAction: (NavigationAction) -> Unit = sessionNavigationViewModel()::onAction,
    // 탈퇴 성공 콜백 — 호출부가 기존 로그아웃 경로(App.kt onLogout: onSessionEnd+LoginViewModel.Action.Logout)로 이어 붙인다
    onAccountDeleted: () -> Unit = {},
    viewModel: AccountSettingsViewModel = screenViewModel {
        AccountSettingsViewModel(
            getMyProfileUseCase = it.getMyProfileUseCase,
            updateMyProfileUseCase = it.updateMyProfileUseCase,
            changePasswordUseCase = it.changePasswordUseCase,
            uploadImageUseCase = it.uploadImageUseCase,
            deleteAccountUseCase = it.deleteAccountUseCase
        )
    },
    // 저장 성공 반영용 — 프로필 탭/드로어 헤더와 같은 세션 스코프 인스턴스
    profileViewModel: ProfileViewModel = sessionViewModel { ProfileViewModel(it.getMyProfileUseCase) }
) {
    val uiState by viewModel.uiState.collectAsState()
    val onAction = viewModel::onAction
    val sg = SgTheme.colors
    var name by rememberSaveable { mutableStateOf("") }
    var statusMessage by rememberSaveable { mutableStateOf("") }
    var bio by rememberSaveable { mutableStateOf("") }
    // 폼 초기값은 프로필 로드 후 1회만 주입 — 이후엔 사용자 입력이 우선(재로드에 덮이지 않게)
    var formFilled by rememberSaveable { mutableStateOf(false) }

    // 비밀번호는 저장 복원 대상이 아니다 — rememberSaveable 대신 remember
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var profileSaved by remember { mutableStateOf(false) }
    var passwordChanged by remember { mutableStateOf(false) }
    // 탈퇴 확인 다이얼로그 — 레거시 그룹 삭제/나가기 CloseConfirmDialog 관용구 미러
    var confirmingDelete by remember { mutableStateOf(false) }
    var deletePassword by remember { mutableStateOf("") }
    // 이번에 다이얼로그를 연 뒤 실제로 제출한 적이 있을 때만 VM 에러를 보여준다 — 취소 후 재오픈 시
    // 직전 실패 문구(예: "현재 비밀번호가 올바르지 않습니다")가 입력 전인데 먼저 보이는 문제 방지.
    // VM은 세션 스코프(uiState)가 다이얼로그보다 오래 살아 에러가 자연 소멸하지 않으므로 화면 로컬로 게이트.
    var deleteAttempted by remember { mutableStateOf(false) }
    val pickProfileImage = rememberImagePickerLauncher { picked ->
        onAction(AccountSettingsViewModel.Action.ChangeProfileImage(picked.bytes, picked.fileName, picked.contentType))
    }

    LaunchedEffect(uiState.profile) {
        val profile = uiState.profile
        if (profile != null && !formFilled) {
            name = profile.name
            statusMessage = profile.statusMessage.orEmpty()
            bio = profile.bio.orEmpty()
            formFilled = true
        }
    }
    // 일회성 이벤트 수집 — 성공 안내 문구는 웹 미러
    LaunchedEffect(viewModel) {
        viewModel.event.collect { event ->
            when (event) {
                AccountSettingsViewModel.Event.ProfileSaved -> {
                    profileSaved = true
                    profileViewModel.onAction(ProfileViewModel.Action.Load)
                }
                AccountSettingsViewModel.Event.PasswordChanged -> {
                    passwordChanged = true
                    currentPassword = ""
                    newPassword = ""
                    confirmPassword = ""
                }
                AccountSettingsViewModel.Event.AccountDeleted -> {
                    confirmingDelete = false
                    deletePassword = ""
                    deleteAttempted = false
                    onAccountDeleted()
                }
            }
        }
    }
    Column(modifier.fillMaxSize().background(sg.paper).imePadding()) {
        SgTopBar(
            title = stringResource(Res.string.profile_account_settings),
            navigationIcon = {
                // 탈퇴 요청 in-flight 중 pop되면 백스택 스코프 VM이 취소돼 Event.AccountDeleted를
                // 놓친다(서버는 삭제됐는데 로그아웃 미발화) — iosApp navigationBarBackButtonHidden 미러.
                // 시스템 백/스와이프 제스처 잔여는 iOS 스와이프백과 동일하게 수용 리스크로 남겨둔다.
                IconButton(
                    onClick = { onNavigationAction(NavigationAction.NavigateBack) },
                    enabled = !uiState.isDeletingAccount
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.common_back))
                }
            }
        )
        when {
            uiState.profile == null && uiState.isLoading -> Box(
                Modifier.fillMaxWidth().padding(vertical = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = sg.accent)
            }
            uiState.profile == null && uiState.loadError != null -> Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(uiState.loadError!!, style = SgTheme.typography.bodyMedium, color = sg.rust)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { onAction(AccountSettingsViewModel.Action.Load) }) {
                    Text(stringResource(Res.string.common_retry), color = sg.accent)
                }
            }
            else -> Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SgCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            stringResource(Res.string.common_profile),
                            style = SgTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = sg.ink
                        )
                        // 웹 폼 상단 미러 — 아바타(탭하면 변경)+이메일(이메일은 수정 불가)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable(enabled = !uiState.isUploadingImage, onClick = pickProfileImage),
                                contentAlignment = Alignment.Center
                            ) {
                                SgAvatar(
                                    name.ifBlank { "?" },
                                    size = 48.dp,
                                    imageUrl = uiState.displayedProfileImg
                                )
                                if (uiState.isUploadingImage) {
                                    Box(
                                        Modifier.matchParentSize().background(sg.ink.copy(alpha = 0.4f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = sg.onAccent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                } else {
                                    Icon(
                                        Icons.Default.PhotoCamera,
                                        contentDescription = stringResource(Res.string.account_change_photo),
                                        tint = sg.onAccent,
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(18.dp)
                                            .background(sg.ink, CircleShape)
                                            .padding(3.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                uiState.profile?.email.orEmpty(),
                                style = SgTheme.typography.bodySmall,
                                color = sg.inkFaint
                            )
                        }
                        SgTextField(
                            value = name,
                            onValueChange = { if (it.length <= 50) name = it },
                            label = stringResource(Res.string.auth_name),
                            enabled = !uiState.isSaving
                        )
                        SgTextField(
                            value = statusMessage,
                            onValueChange = { if (it.length <= 100) statusMessage = it },
                            label = stringResource(Res.string.account_status_message),
                            enabled = !uiState.isSaving
                        )
                        SgTextField(
                            value = bio,
                            onValueChange = { if (it.length <= 500) bio = it },
                            label = stringResource(Res.string.account_bio),
                            singleLine = false,
                            minLines = 4,
                            enabled = !uiState.isSaving
                        )
                        uiState.saveError?.let {
                            Text(it, style = SgTheme.typography.bodySmall, color = sg.rust)
                        }
                        if (profileSaved) {
                            Text(stringResource(Res.string.account_saved), style = SgTheme.typography.bodySmall, color = sg.accent)
                        }
                        SgPrimaryButton(
                            text = stringResource(Res.string.common_save),
                            onClick = {
                                profileSaved = false
                                onAction(AccountSettingsViewModel.Action.SaveProfile(name, bio, statusMessage))
                            },
                            isLoading = uiState.isSaving
                        )
                    }
                }
                SgCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            stringResource(Res.string.account_change_password),
                            style = SgTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = sg.ink
                        )
                        SgTextField(
                            value = currentPassword,
                            onValueChange = { currentPassword = it },
                            label = stringResource(Res.string.account_current_password),
                            isPassword = true,
                            enabled = !uiState.isChangingPassword
                        )
                        SgTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            label = stringResource(Res.string.account_new_password),
                            isPassword = true,
                            enabled = !uiState.isChangingPassword
                        )
                        SgTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = stringResource(Res.string.account_confirm_password),
                            isPassword = true,
                            enabled = !uiState.isChangingPassword
                        )
                        uiState.passwordError?.let {
                            Text(it, style = SgTheme.typography.bodySmall, color = sg.rust)
                        }
                        if (passwordChanged) {
                            Text(
                                stringResource(Res.string.account_password_changed),
                                style = SgTheme.typography.bodySmall,
                                color = sg.accent
                            )
                        }
                        SgPrimaryButton(
                            text = stringResource(Res.string.account_change_password),
                            onClick = {
                                passwordChanged = false
                                onAction(
                                    AccountSettingsViewModel.Action.ChangePassword(
                                        currentPassword = currentPassword,
                                        newPassword = newPassword,
                                        confirmPassword = confirmPassword
                                    )
                                )
                            },
                            isLoading = uiState.isChangingPassword
                        )
                    }
                }
                // 설정 목록 끝 — 회원 탈퇴(위험색 행, 탭하면 확인 다이얼로그)
                SgCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !uiState.isDeletingAccount) {
                                // 새로 여는 참이니 직전(취소된) 시도의 잔존 에러는 숨긴다
                                deleteAttempted = false
                                confirmingDelete = true
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(Res.string.account_delete_account),
                            style = SgTheme.typography.bodyLarge,
                            color = sg.rust,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
    if (confirmingDelete) {
        DeleteAccountDialog(
            password = deletePassword,
            onPasswordChange = { deletePassword = it },
            isLoading = uiState.isDeletingAccount,
            // 이번 오픈에서 한 번이라도 제출했을 때만 VM 에러를 노출(위 deleteAttempted 주석 참고)
            error = uiState.deleteAccountError.takeIf { deleteAttempted },
            onDismiss = {
                confirmingDelete = false
                deletePassword = ""
                deleteAttempted = false
            },
            onConfirm = {
                deleteAttempted = true
                onAction(AccountSettingsViewModel.Action.DeleteAccount(deletePassword))
            }
        )
    }
}

/**
 * 회원 탈퇴 확인 다이얼로그 — GroupSettingsTab CloseConfirmDialog(삭제/나가기) 관용구 미러
 * + 본인 확인용 비밀번호 필드. 성공하면 Event.AccountDeleted로 화면이 닫고 로그아웃 흐름으로 넘어간다.
 */
@Composable
private fun DeleteAccountDialog(
    password: String,
    onPasswordChange: (String) -> Unit,
    isLoading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val sg = SgTheme.colors

    Dialog(onDismissRequest = onDismiss) {
        SgCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(Res.string.account_delete_account),
                    style = SgTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = sg.ink
                )
                Text(stringResource(Res.string.account_delete_confirm), style = SgTheme.typography.bodyMedium, color = sg.ink)
                SgTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = stringResource(Res.string.account_current_password),
                    isPassword = true,
                    enabled = !isLoading
                )
                error?.let {
                    Text(it, style = SgTheme.typography.bodySmall, color = sg.rust)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isLoading,
                        shape = SgTheme.shapes.button,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(Res.string.common_cancel), color = sg.ink)
                    }
                    Button(
                        onClick = onConfirm,
                        enabled = !isLoading && password.isNotBlank(),
                        shape = SgTheme.shapes.button,
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = sg.rust,
                            contentColor = sg.onAccent,
                            disabledBackgroundColor = sg.rust.copy(alpha = 0.4f),
                            disabledContentColor = sg.inkFaint
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = sg.inkFaint)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(Res.string.account_delete_action), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
