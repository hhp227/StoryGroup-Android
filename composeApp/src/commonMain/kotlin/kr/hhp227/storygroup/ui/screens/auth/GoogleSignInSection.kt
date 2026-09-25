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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.ui.auth.rememberGoogleSignInLauncher
import kr.hhp227.storygroup.ui.components.SgPrimaryButton
import kr.hhp227.storygroup.ui.theme.SgTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import storygroup.composeapp.generated.resources.Res
import storygroup.composeapp.generated.resources.ic_google_g
import storygroup.composeapp.generated.resources.login_google
import storygroup.composeapp.generated.resources.login_or

/**
 * "또는" 구분선 + 구글 버튼(로그인 버튼과 같은 SgPrimaryButton) — 로그인·가입 화면 공용.
 * 구글은 가입과 로그인이 한 경로라 두 화면 모두 세션 VM(LoginViewModel)으로 보낸다.
 * 플랫폼 클라이언트 ID가 없으면 섹션 자체를 그리지 않는다. 아래 여백은 호출부가 둔다
 */
@Composable
fun GoogleSignInSection(
    enabled: Boolean,
    onAction: (LoginViewModel.Action) -> Unit
) {
    val googleSignIn = rememberGoogleSignInLauncher()
    val scope = rememberCoroutineScope()

    if (!googleSignIn.isAvailable) return
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
    SgPrimaryButton(
        text = stringResource(Res.string.login_google),
        onClick = {
            scope.launch {
                // 취소(null)는 조용히 — 그 밖의 런처 실패만 에러로 올린다
                runCatching { googleSignIn.signIn() }
                    .onSuccess { credential -> credential?.let { onAction(LoginViewModel.Action.GoogleLogin(it)) } }
                    .onFailure { e -> onAction(LoginViewModel.Action.GoogleLoginFailed(e.message)) }
            }
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        leadingIcon = { GoogleLogo() }
    )
    Spacer(Modifier.height(20.dp))
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
