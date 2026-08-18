package kr.hhp227.storygroup.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
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

/** 입력 바 필드의 최소 높이 — 바 총 높이 = 이 값 + 바깥 Row의 세로 패딩 2배 */
private val ComposerFieldMinHeight = 40.dp

/**
 * 하단 입력 바 전용 슬림 필드 — 레거시 EditText(background="@null") 미러.
 *
 * SgTextField가 쓰는 M2 OutlinedTextField는 최소 높이가 56dp로 고정이라 한 줄짜리
 * 댓글/메시지 입력에는 지나치게 두껍다. 여기서는 테두리도 배경도 그리지 않고 바 배경 위에
 * 글자만 얹어, 터치 영역만 40dp로 확보한다(레거시 컨테이너 패딩 5dp + wrap_content EditText).
 */
@Composable
fun SgComposerField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val sg = SgTheme.colors

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        singleLine = true,
        textStyle = SgTheme.typography.bodyMedium.copy(color = if (enabled) sg.ink else sg.inkFaint),
        cursorBrush = SolidColor(sg.accent),
        decorationBox = { innerTextField ->
            Box(
                // 세로 여백은 바깥 입력 바가 준다 — 여기는 레거시 paddingStart 5dp 자리만
                Modifier.fillMaxWidth().heightIn(min = ComposerFieldMinHeight).padding(horizontal = 6.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(placeholder, style = SgTheme.typography.bodyMedium, color = sg.inkFaint)
                }
                innerTextField()
            }
        }
    )
}
