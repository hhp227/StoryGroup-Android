package kr.hhp227.storygroup.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kr.hhp227.storygroup.ui.theme.SgTheme

/** 공용 주 버튼 — 웹 .btn-primary 미러(accent 채움, warm=필/vibrant=8dp는 SgTheme.shapes.button) */
@Composable
fun SgPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    val sg = SgTheme.colors

    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled && !isLoading,
        shape = SgTheme.shapes.button,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = sg.accent,
            contentColor = sg.onAccent,
            disabledBackgroundColor = sg.accentSoft,
            disabledContentColor = sg.inkFaint
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = sg.inkFaint
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.Bold)
    }
}
