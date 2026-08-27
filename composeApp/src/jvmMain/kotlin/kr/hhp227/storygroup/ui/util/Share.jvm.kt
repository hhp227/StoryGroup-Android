package kr.hhp227.storygroup.ui.util

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import kotlinx.coroutines.delay
import kr.hhp227.storygroup.ui.theme.SgTheme
import org.jetbrains.compose.resources.stringResource
import storygroup.composeapp.generated.resources.Res
import storygroup.composeapp.generated.resources.share_copied

/** Desktop엔 공유 시트가 없어 클립보드 복사로 대신하고, 잠깐 뜨는 팝업으로 복사됐음을 알린다 */
@Composable
actual fun rememberShareLauncher(): (String) -> Unit {
    val clipboard = LocalClipboardManager.current
    var copiedTick by remember { mutableStateOf(0) }

    if (copiedTick > 0) {
        val sg = SgTheme.colors

        Popup(alignment = Alignment.BottomCenter) {
            Text(
                stringResource(Res.string.share_copied),
                style = SgTheme.typography.bodySmall,
                color = sg.paper,
                modifier = Modifier
                    .padding(24.dp)
                    .background(sg.ink, SgTheme.shapes.button)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
        LaunchedEffect(copiedTick) {
            delay(1500)
            copiedTick = 0
        }
    }
    return { text ->
        clipboard.setText(AnnotatedString(text))
        copiedTick++
    }
}
