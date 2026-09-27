package kr.hhp227.storygroup.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kr.hhp227.storygroup.ui.auth.rememberAppleSignInLauncher
import kr.hhp227.storygroup.ui.auth.rememberGoogleSignInLauncher
import kr.hhp227.storygroup.ui.theme.SgTheme
import org.jetbrains.compose.resources.stringResource
import storygroup.composeapp.generated.resources.Res
import storygroup.composeapp.generated.resources.login_or

/**
 * "또는" 구분선 + 애플 → 구글(애플 HIG: 애플 버튼을 다른 소셜 버튼보다 덜 눈에 띄게 두지 않는다).
 * 로그인·가입 화면 공용 — 둘 다 가입=로그인이라 세션 VM(LoginViewModel)으로 보낸다.
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
