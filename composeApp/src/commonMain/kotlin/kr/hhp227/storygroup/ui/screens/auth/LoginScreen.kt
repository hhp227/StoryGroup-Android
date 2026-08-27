package kr.hhp227.storygroup.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kr.hhp227.storygroup.di.screenViewModel
import kr.hhp227.storygroup.ui.components.SgCard
import kr.hhp227.storygroup.ui.components.SgPrimaryButton
import kr.hhp227.storygroup.ui.components.SgTextField
import kr.hhp227.storygroup.ui.theme.SgTheme
import org.jetbrains.compose.resources.stringResource
import storygroup.composeapp.generated.resources.Res
import storygroup.composeapp.generated.resources.auth_email
import storygroup.composeapp.generated.resources.auth_password
import storygroup.composeapp.generated.resources.login_action
import storygroup.composeapp.generated.resources.login_in_progress
import storygroup.composeapp.generated.resources.login_no_account
import storygroup.composeapp.generated.resources.login_registered_notice
import storygroup.composeapp.generated.resources.login_subtitle
import storygroup.composeapp.generated.resources.login_title
import storygroup.composeapp.generated.resources.register_action

/** 로그인 — 웹 /login 미러. VM은 세션 게이트라 App 루트와 같은 인스턴스를 default parameter로 선언한다 */
@Composable
fun LoginScreen(
    justRegistered: Boolean,
    onNavigateToRegister: () -> Unit,
    viewModel: LoginViewModel = screenViewModel {
        LoginViewModel(it.isLoggedInUseCase, it.loginUseCase, it.logoutUseCase)
    }
) {
    val uiState by viewModel.uiState.collectAsState()
    val onAction = viewModel::onAction
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SgTheme.colors.paper)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            stringResource(Res.string.login_title),
            style = SgTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = SgTheme.colors.ink
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(Res.string.login_subtitle),
            style = SgTheme.typography.bodyMedium,
            color = SgTheme.colors.inkSoft
        )
        Spacer(Modifier.height(28.dp))
        if (justRegistered) {
            SgCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(Res.string.login_registered_notice),
                    style = SgTheme.typography.bodyMedium,
                    color = SgTheme.colors.moss,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
            Spacer(Modifier.height(20.dp))
        }
        SgTextField(
            value = email,
            onValueChange = { email = it },
            label = stringResource(Res.string.auth_email),
            keyboardType = KeyboardType.Email,
            enabled = !uiState.isLoading
        )
        Spacer(Modifier.height(16.dp))
        SgTextField(
            value = password,
            onValueChange = { password = it },
            label = stringResource(Res.string.auth_password),
            isPassword = true,
            keyboardType = KeyboardType.Password,
            enabled = !uiState.isLoading
        )
        uiState.error?.let { error ->
            Spacer(Modifier.height(12.dp))
            Text(error, style = SgTheme.typography.bodySmall, color = SgTheme.colors.rust)
        }
        Spacer(Modifier.height(24.dp))
        SgPrimaryButton(
            text = if (uiState.isLoading) stringResource(Res.string.login_in_progress) else stringResource(Res.string.login_action),
            onClick = { onAction(LoginViewModel.Action.Login(email.trim(), password)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = email.isNotBlank() && password.isNotBlank(),
            isLoading = uiState.isLoading
        )
        Spacer(Modifier.height(20.dp))
        Row {
            Text(stringResource(Res.string.login_no_account), style = SgTheme.typography.bodyMedium, color = SgTheme.colors.inkSoft)
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(Res.string.register_action),
                style = SgTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = SgTheme.colors.accent,
                modifier = Modifier.clickable(enabled = !uiState.isLoading) {
                    // 화면을 떠나며 자기 에러를 정리한다(이전엔 AuthFlow 몫)
                    onAction(LoginViewModel.Action.ClearError)
                    onNavigateToRegister()
                }
            )
        }
    }
}
