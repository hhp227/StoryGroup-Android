package kr.hhp227.storygroup.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kr.hhp227.storygroup.ui.theme.SgTheme

/** 공용 입력 필드 — 웹 .field 미러(라벨 위 배치 + linen 입력창). label이 null이면 입력창만 그린다 */
@Composable
fun SgTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String? = null,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
    // 멀티라인 입력(소개 등) — 웹 textarea 미러
    singleLine: Boolean = true,
    minLines: Int = 1
) {
    val sg = SgTheme.colors

    Column(modifier) {
        if (label != null) {
            Text(
                label,
                style = SgTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = sg.inkSoft
            )
            Spacer(Modifier.height(6.dp))
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = singleLine,
            minLines = minLines,
            enabled = enabled,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = SgTheme.shapes.field,
            colors = TextFieldDefaults.outlinedTextFieldColors(
                textColor = sg.ink,
                disabledTextColor = sg.inkFaint,
                backgroundColor = sg.linen,
                cursorColor = sg.accent,
                focusedBorderColor = sg.accent,
                unfocusedBorderColor = sg.stoneBorder,
                disabledBorderColor = sg.stoneBorder
            )
        )
    }
}
