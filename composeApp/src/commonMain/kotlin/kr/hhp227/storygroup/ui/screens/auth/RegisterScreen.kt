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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kr.hhp227.storygroup.ui.components.SgPrimaryButton
import kr.hhp227.storygroup.ui.components.SgTextField
import kr.hhp227.storygroup.ui.theme.SgTheme

/** 가입 — 웹 /register 미러. 성공 시 상위(AuthFlow)가 Event.Registered를 받아 로그인으로 되돌린다(MVI) */
@Composable
fun RegisterScreen(
    uiState: RegisterViewModel.UiState,
    onAction: (RegisterViewModel.Action) -> Unit,
    onNavigateToLogin: () -> Unit
) {
    var name by remember { mutableStateOf("") }
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
            "같이할 사람들을 위한 자리",
            style = SgTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = SgTheme.colors.ink
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "StoryGroup에 가입하고 그룹을 만들어보세요.",
            style = SgTheme.typography.bodyMedium,
            color = SgTheme.colors.inkSoft
        )
        Spacer(Modifier.height(28.dp))
        SgTextField(
            value = name,
            onValueChange = { name = it },
            label = "이름",
            enabled = !uiState.isLoading
        )
        Spacer(Modifier.height(16.dp))
        SgTextField(
            value = email,
            onValueChange = { email = it },
            label = "이메일",
            keyboardType = KeyboardType.Email,
            enabled = !uiState.isLoading
        )
        Spacer(Modifier.height(16.dp))
        SgTextField(
            value = password,
            onValueChange = { password = it },
            label = "비밀번호",
            isPassword = true,
            keyboardType = KeyboardType.Password,
            enabled = !uiState.isLoading
        )
        if (uiState.error != null) {
            Spacer(Modifier.height(12.dp))
            Text(uiState.error!!, style = SgTheme.typography.bodySmall, color = SgTheme.colors.rust)
        }
        Spacer(Modifier.height(24.dp))
        SgPrimaryButton(
            text = if (uiState.isLoading) "가입하는 중..." else "가입하기",
            onClick = { onAction(RegisterViewModel.Action.Register(name.trim(), email.trim(), password)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = name.isNotBlank() && email.isNotBlank() && password.isNotBlank(),
            isLoading = uiState.isLoading
        )
        Spacer(Modifier.height(20.dp))
        Row {
            Text("이미 계정이 있나요?", style = SgTheme.typography.bodyMedium, color = SgTheme.colors.inkSoft)
            Spacer(Modifier.width(6.dp))
            Text(
                "로그인",
                style = SgTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = SgTheme.colors.accent,
                modifier = Modifier.clickable(enabled = !uiState.isLoading, onClick = onNavigateToLogin)
            )
        }
    }
}
