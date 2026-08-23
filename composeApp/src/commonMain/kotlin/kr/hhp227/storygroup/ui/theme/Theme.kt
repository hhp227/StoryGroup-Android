package kr.hhp227.storygroup.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Shapes
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.hhp227.storygroup.shared.domain.storage.KeyValueStorage

enum class Mood { WARM, VIBRANT }

enum class NightMode { SYSTEM, LIGHT, DARK }

enum class NavStyle { TABS, DRAWER }

/** 테마/내비 설정 상태 홀더 — 생성 시 KeyValueStorage에서 복원, 변경 즉시 저장 */
@Stable
class ThemeState(private val storage: KeyValueStorage) {
    private var moodState by mutableStateOf(storage.restore(KEY_MOOD, Mood.WARM))
    private var nightModeState by mutableStateOf(storage.restore(KEY_NIGHT_MODE, NightMode.SYSTEM))
    private var navStyleState by mutableStateOf(storage.restore(KEY_NAV_STYLE, NavStyle.TABS))

    var mood: Mood
        get() = moodState
        set(value) {
            moodState = value
            storage.putString(KEY_MOOD, value.name)
        }

    var nightMode: NightMode
        get() = nightModeState
        set(value) {
            nightModeState = value
            storage.putString(KEY_NIGHT_MODE, value.name)
        }

    var navStyle: NavStyle
        get() = navStyleState
        set(value) {
            navStyleState = value
            storage.putString(KEY_NAV_STYLE, value.name)
        }

    private companion object {
        const val KEY_MOOD = "theme_mood"
        const val KEY_NIGHT_MODE = "theme_night_mode"
        const val KEY_NAV_STYLE = "theme_nav_style"
    }
}

private inline fun <reified E : Enum<E>> KeyValueStorage.restore(key: String, default: E): E =
    getString(key)?.let { saved -> enumValues<E>().firstOrNull { it.name == saved } } ?: default

/**
 * 타이포 스케일 — 이름은 M3 타입 스케일(titleMedium 등)을 그대로 쓰고 값도 M3 기본값과 동일.
 * 화면이 MaterialTheme.typography 대신 이걸 보므로, M2↔M3 어느 쪽이든 화면 코드는 안 바뀐다.
 */
@Immutable
data class SgTypography(
    val headlineSmall: TextStyle = TextStyle(fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 32.sp),
    val titleLarge: TextStyle = TextStyle(fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 28.sp),
    val titleMedium: TextStyle = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    val titleSmall: TextStyle = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    val bodyLarge: TextStyle = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    val bodyMedium: TextStyle = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    val bodySmall: TextStyle = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    val labelLarge: TextStyle = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    val labelSmall: TextStyle = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp)
)

/** 모양 토큰 — 웹 미러: warm=버튼 필(50%)·카드 18dp·입력 10dp, vibrant=버튼 8dp·카드 12dp·입력 6dp */
@Immutable
data class SgShapes(
    val field: CornerBasedShape,
    val button: CornerBasedShape,
    val card: CornerBasedShape
)

internal val LocalSgTypography = staticCompositionLocalOf { SgTypography() }
internal val LocalSgShapes = staticCompositionLocalOf {
    SgShapes(field = RoundedCornerShape(10.dp), button = RoundedCornerShape(50), card = RoundedCornerShape(18.dp))
}

/**
 * 디자인 시스템 파사드 — 화면·컴포넌트는 MaterialTheme가 아니라 이걸 본다.
 * 현재 구현은 레거시 앱과 같은 M2(androidx.compose.material). 나중에 M3로 바꿀 때는
 *  ⑴ build.gradle.kts: compose.material → compose.material3
 *  ⑵ 이 파일: colors→colorScheme 매핑만 교체(StoryGroupTheme)
 *  ⑶ M 위젯을 직접 쓰는 파일(ui/components, ui/shell, Groups·Profile·Settings 화면)의 import 치환
 * 만 하면 되고, SgTheme.colors/typography/shapes를 쓰는 화면 코드는 그대로다.
 */
object SgTheme {
    val colors: SgColors
        @Composable get() = LocalSgColors.current
    val typography: SgTypography
        @Composable get() = LocalSgTypography.current
    val shapes: SgShapes
        @Composable get() = LocalSgShapes.current
}

@Composable
fun StoryGroupTheme(
    mood: Mood = Mood.WARM,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val sg = when (mood) {
        Mood.WARM -> if (darkTheme) WarmDarkColors else WarmLightColors
        Mood.VIBRANT -> if (darkTheme) VibrantDarkColors else VibrantLightColors
    }
    val colors = if (darkTheme) {
        darkColors(
            primary = sg.accent,
            primaryVariant = sg.accent,
            secondary = sg.accent2,
            background = sg.paper,
            surface = sg.linen,
            error = sg.rust,
            onPrimary = sg.onAccent,
            onSecondary = sg.onAccent,
            onBackground = sg.ink,
            onSurface = sg.ink,
            onError = sg.onAccent
        )
    } else {
        lightColors(
            primary = sg.accent,
            primaryVariant = sg.accent,
            secondary = sg.accent2,
            secondaryVariant = sg.accent2,
            background = sg.paper,
            surface = sg.linen,
            error = sg.rust,
            onPrimary = sg.onAccent,
            onSecondary = sg.onAccent,
            onBackground = sg.ink,
            onSurface = sg.ink,
            onError = sg.onAccent
        )
    }
    val sgShapes = when (mood) {
        Mood.WARM -> SgShapes(
            field = RoundedCornerShape(10.dp),
            button = RoundedCornerShape(50),
            card = RoundedCornerShape(18.dp)
        )
        Mood.VIBRANT -> SgShapes(
            field = RoundedCornerShape(6.dp),
            button = RoundedCornerShape(8.dp),
            card = RoundedCornerShape(12.dp)
        )
    }

    CompositionLocalProvider(
        LocalSgColors provides sg,
        LocalSgTypography provides SgTypography(),
        LocalSgShapes provides sgShapes
    ) {
        MaterialTheme(
            colors = colors,
            // Sg 컴포넌트는 SgTheme.shapes를 직접 쓰므로 여기는 M2 위젯 기본값용 매핑
            shapes = Shapes(small = sgShapes.field, medium = sgShapes.card, large = sgShapes.card),
            content = content
        )
    }
}
