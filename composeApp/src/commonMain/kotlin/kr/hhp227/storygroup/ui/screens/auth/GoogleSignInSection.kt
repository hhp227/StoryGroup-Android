package kr.hhp227.storygroup.ui.screens.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.ui.auth.GoogleSignInLauncher
import kr.hhp227.storygroup.ui.components.SgPrimaryButton
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import storygroup.composeapp.generated.resources.Res
import storygroup.composeapp.generated.resources.ic_google_g
import storygroup.composeapp.generated.resources.login_google

/** 구글 버튼(로그인 버튼과 같은 SgPrimaryButton) — SocialSignInSection이 구분선·애플 버튼과 함께 배치한다 */
@Composable
fun GoogleSignInButton(
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
