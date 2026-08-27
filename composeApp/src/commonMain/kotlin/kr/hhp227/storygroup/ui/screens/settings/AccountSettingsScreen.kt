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
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
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
import storygroup.composeapp.generated.resources.account_new_password
import storygroup.composeapp.generated.resources.account_password_changed
import storygroup.composeapp.generated.resources.account_saved
import storygroup.composeapp.generated.resources.account_status_message
import storygroup.composeapp.generated.resources.auth_name
import storygroup.composeapp.generated.resources.common_back
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
    viewModel: AccountSettingsViewModel = screenViewModel {
        AccountSettingsViewModel(
            getMyProfileUseCase = it.getMyProfileUseCase,
            updateMyProfileUseCase = it.updateMyProfileUseCase,
            changePasswordUseCase = it.changePasswordUseCase,
            uploadImageUseCase = it.uploadImageUseCase
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
            }
        }
    }
    Column(modifier.fillMaxSize().background(sg.paper).imePadding()) {
        SgTopBar(
            title = stringResource(Res.string.profile_account_settings),
            navigationIcon = {
                IconButton(onClick = { onNavigationAction(NavigationAction.NavigateBack) }) {
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
            }
        }
    }
}
