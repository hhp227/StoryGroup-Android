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
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.hhp227.storygroup.di.LocalAppContainer
import kr.hhp227.storygroup.di.sessionViewModel
import kr.hhp227.storygroup.ui.components.SgAvatar
import kr.hhp227.storygroup.ui.components.SgCard
import kr.hhp227.storygroup.ui.components.SgPrimaryButton
import kr.hhp227.storygroup.ui.components.SgTextField
import kr.hhp227.storygroup.ui.components.SgTopBar
import kr.hhp227.storygroup.ui.screens.profile.ProfileViewModel
import kr.hhp227.storygroup.ui.theme.SgTheme
import kr.hhp227.storygroup.ui.util.rememberImagePickerLauncher

@Composable
private fun accountSettingsViewModel(): AccountSettingsViewModel {
    val container = LocalAppContainer.current

    return viewModel {
        AccountSettingsViewModel(
            getMyProfileUseCase = container.getMyProfileUseCase,
            updateMyProfileUseCase = container.updateMyProfileUseCase,
            changePasswordUseCase = container.changePasswordUseCase,
            uploadImageUseCase = container.uploadImageUseCase
        )
    }
}

/**
 * 계정 설정 — 프로필 수정+비밀번호 변경(웹 /settings/profile·password 두 페이지를 한 화면 두 카드로).
 * NavHost 풀스크린 목적지라 상단바는 화면이 소유한다. 폼 필드는 화면 소유(CreatePost 패턴),
 * 저장 성공 시 세션 ProfileViewModel을 갱신해 프로필 탭/드로어 헤더에 반영한다.
 * iosApp AccountSettingsView.swift와 1:1 미러
 */
@Composable
fun AccountSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountSettingsViewModel = accountSettingsViewModel(),
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
            title = "계정 설정",
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
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
                    Text("다시 시도", color = sg.accent)
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
                            "프로필",
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
                                        contentDescription = "프로필 이미지 변경",
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
                            label = "이름",
                            enabled = !uiState.isSaving
                        )
                        SgTextField(
                            value = statusMessage,
                            onValueChange = { if (it.length <= 100) statusMessage = it },
                            label = "상태메시지",
                            enabled = !uiState.isSaving
                        )
                        SgTextField(
                            value = bio,
                            onValueChange = { if (it.length <= 500) bio = it },
                            label = "소개",
                            singleLine = false,
                            minLines = 4,
                            enabled = !uiState.isSaving
                        )
                        uiState.saveError?.let {
                            Text(it, style = SgTheme.typography.bodySmall, color = sg.rust)
                        }
                        if (profileSaved) {
                            Text("저장했습니다.", style = SgTheme.typography.bodySmall, color = sg.accent)
                        }
                        SgPrimaryButton(
                            text = "저장",
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
                            "비밀번호 변경",
                            style = SgTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = sg.ink
                        )
                        SgTextField(
                            value = currentPassword,
                            onValueChange = { currentPassword = it },
                            label = "현재 비밀번호",
                            isPassword = true,
                            enabled = !uiState.isChangingPassword
                        )
                        SgTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            label = "새 비밀번호 (8자 이상)",
                            isPassword = true,
                            enabled = !uiState.isChangingPassword
                        )
                        SgTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = "새 비밀번호 확인",
                            isPassword = true,
                            enabled = !uiState.isChangingPassword
                        )
                        uiState.passwordError?.let {
                            Text(it, style = SgTheme.typography.bodySmall, color = sg.rust)
                        }
                        if (passwordChanged) {
                            Text(
                                "비밀번호를 변경했습니다. 다른 기기에서는 다시 로그인해야 합니다.",
                                style = SgTheme.typography.bodySmall,
                                color = sg.accent
                            )
                        }
                        SgPrimaryButton(
                            text = "비밀번호 변경",
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
