package kr.hhp227.storygroup.ui.screens.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.ui.auth.AppleSignInLauncher
import kr.hhp227.storygroup.ui.theme.SgTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import storygroup.composeapp.generated.resources.Res
import storygroup.composeapp.generated.resources.ic_apple_logo
import storygroup.composeapp.generated.resources.login_apple

/** 애플 HIG — 검정 배경·흰 로고, 크기·모양은 SgPrimaryButton(48dp, SgTheme.shapes.button)과 같게 */
@Composable
fun AppleSignInButton(
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
