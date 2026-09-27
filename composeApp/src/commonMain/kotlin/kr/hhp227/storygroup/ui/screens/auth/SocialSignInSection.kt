package kr.hhp227.storygroup.ui.screens.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.ui.auth.AppleSignInLauncher
import kr.hhp227.storygroup.ui.auth.GoogleSignInLauncher
import kr.hhp227.storygroup.ui.auth.rememberAppleSignInLauncher
import kr.hhp227.storygroup.ui.auth.rememberGoogleSignInLauncher
import kr.hhp227.storygroup.ui.components.SgPrimaryButton
import kr.hhp227.storygroup.ui.theme.SgTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import storygroup.composeapp.generated.resources.Res
import storygroup.composeapp.generated.resources.ic_apple_logo
import storygroup.composeapp.generated.resources.ic_google_g
import storygroup.composeapp.generated.resources.login_apple
import storygroup.composeapp.generated.resources.login_google
import storygroup.composeapp.generated.resources.login_or

/**
 * "또는" 구분선 + 애플 → 구글(애플 HIG: 애플 버튼을 다른 소셜 버튼보다 덜 눈에 띄게 두지 않는다).
 * 로그인·가입 화면 공용 — 둘 다 가입=로그인이라 세션 VM(LoginViewModel)으로 보낸다.
 * 버튼은 화면 전용(VM Action·플랫폼 런처에 묶임)이라 components가 아닌 이 파일의 private 부품이다(iosApp LoginView.swift 미러).
 * 쓸 수 있는 버튼이 하나도 없으면 아무것도 그리지 않는다. 아래 여백은 호출부가 둔다
 */
@Composable
fun SocialSignInSection(
    enabled: Boolean,
    onAction: (LoginViewModel.Action) -> Unit
) {
    val apple = rememberAppleSignInLauncher()
    val google = rememberGoogleSignInLauncher()
    if (!apple.isAvailable && !google.isAvailable) return

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(SgTheme.colors.stoneBorder))
        Text(
            stringResource(Res.string.login_or),
            style = SgTheme.typography.bodySmall,
            color = SgTheme.colors.inkSoft,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Box(Modifier.weight(1f).height(1.dp).background(SgTheme.colors.stoneBorder))
    }
    Spacer(Modifier.height(20.dp))
    if (apple.isAvailable) {
        AppleSignInButton(apple, enabled, onAction)
        Spacer(Modifier.height(12.dp))
    }
    if (google.isAvailable) {
        GoogleSignInButton(google, enabled, onAction)
        Spacer(Modifier.height(12.dp))
    }
    Spacer(Modifier.height(8.dp))
}

/** 애플 HIG — 검정 배경·흰 로고, 크기·모양은 SgPrimaryButton(48dp, SgTheme.shapes.button)과 같게 */
@Composable
private fun AppleSignInButton(
    launcher: AppleSignInLauncher,
    enabled: Boolean,
    onAction: (LoginViewModel.Action) -> Unit
) {
    val scope = rememberCoroutineScope()
    Button(
        onClick = {
            scope.launch {
                // 취소(null)는 조용히 — 그 밖의 런처 실패만 에러로 올린다
                runCatching { launcher.signIn() }
                    .onSuccess { credential -> credential?.let { onAction(LoginViewModel.Action.AppleLogin(it)) } }
                    .onFailure { e -> onAction(LoginViewModel.Action.AppleLoginFailed(e.message)) }
            }
        },
        modifier = Modifier.fillMaxWidth().height(48.dp),
        enabled = enabled,
        shape = SgTheme.shapes.button,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = Color.Black,
            contentColor = Color.White,
            disabledBackgroundColor = Color.Black.copy(alpha = 0.4f),
            disabledContentColor = Color.White.copy(alpha = 0.7f)
        )
    ) {
        Image(painterResource(Res.drawable.ic_apple_logo), contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(Res.string.login_apple))
    }
}

/** 구글 — 로그인 버튼과 같은 SgPrimaryButton */
@Composable
private fun GoogleSignInButton(
    launcher: GoogleSignInLauncher,
    enabled: Boolean,
    onAction: (LoginViewModel.Action) -> Unit
) {
    val scope = rememberCoroutineScope()
    SgPrimaryButton(
        text = stringResource(Res.string.login_google),
        onClick = {
            scope.launch {
                // 취소(null)는 조용히 — 그 밖의 런처 실패만 에러로 올린다
                runCatching { launcher.signIn() }
                    .onSuccess { credential -> credential?.let { onAction(LoginViewModel.Action.GoogleLogin(it)) } }
                    .onFailure { e -> onAction(LoginViewModel.Action.GoogleLoginFailed(e.message)) }
            }
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        leadingIcon = { GoogleLogo() }
    )
}

/** 구글 브랜드 4색 G — 핑크(accent) 버튼 위에서도 보이도록 흰 원 배지에 얹는다(웹 GoogleLogo 미러) */
@Composable
private fun GoogleLogo() {
    Box(
        modifier = Modifier.size(22.dp).background(Color.White, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Image(painterResource(Res.drawable.ic_google_g), contentDescription = null, modifier = Modifier.size(14.dp))
    }
}
