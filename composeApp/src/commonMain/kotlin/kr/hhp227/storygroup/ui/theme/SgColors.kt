package kr.hhp227.storygroup.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * StoryGroup 디자인 토큰(웹 globals.css CSS 변수 미러) — 무드(warm/vibrant) × 모드(light/dark) 4조합.
 * iOS(SwiftUI)의 SGTheme.swift와 값이 1:1로 같아야 한다.
 */
@Immutable
data class SgColors(
    val paper: Color,
    val linen: Color,
    val ink: Color,
    val inkSoft: Color,
    val inkFaint: Color,
    val stoneBorder: Color,
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val accent2: Color,
    val accent2Soft: Color,
    val moss: Color,
    val amber: Color,
    val rust: Color,
    val isDark: Boolean
)

val WarmLightColors = SgColors(
    paper = Color(0xFFFCF3EF),
    linen = Color(0xFFFFFFFF),
    ink = Color(0xFF3D2B2A),
    inkSoft = Color(0xFF8A716C),
    inkFaint = Color(0xFFB29892),
    stoneBorder = Color(0xFFF1DCD2),
    accent = Color(0xFFC4577C),
    accentSoft = Color(0xFFF7E1EA),
    onAccent = Color(0xFFFFFFFF),
    accent2 = Color(0xFFD9A441),
    accent2Soft = Color(0xFFF7EBD3),
    moss = Color(0xFF6B8F52),
    amber = Color(0xFFC08A2E),
    rust = Color(0xFFB23B3B),
    isDark = false
)

val WarmDarkColors = SgColors(
    paper = Color(0xFF241A1C),
    linen = Color(0xFF2E2224),
    ink = Color(0xFFF7EDE9),
    inkSoft = Color(0xFFC2A9A4),
    inkFaint = Color(0xFF8C7570),
    stoneBorder = Color(0xFF3E2E31),
    accent = Color(0xFFE8839F),
    accentSoft = Color(0x29E8839F),
    onAccent = Color(0xFF241A1C),
    accent2 = Color(0xFFE8BE72),
    accent2Soft = Color(0x29E8BE72),
    moss = Color(0xFF9BC17E),
    amber = Color(0xFFE0B15C),
    rust = Color(0xFFE08A78),
    isDark = true
)

val VibrantLightColors = SgColors(
    paper = Color(0xFFFBF7FA),
    linen = Color(0xFFFFFFFF),
    ink = Color(0xFF221626),
    inkSoft = Color(0xFF6E5E7D),
    inkFaint = Color(0xFF9A8CAE),
    stoneBorder = Color(0xFFEBE1F0),
    accent = Color(0xFFE85A3D),
    accentSoft = Color(0xFFFBE3DC),
    onAccent = Color(0xFFFFFFFF),
    accent2 = Color(0xFFC98A00),
    accent2Soft = Color(0xFFF7E7C4),
    moss = Color(0xFF2F8F4E),
    amber = Color(0xFFC97A2E),
    rust = Color(0xFFC23B2E),
    isDark = false
)

val VibrantDarkColors = SgColors(
    paper = Color(0xFF1E1626),
    linen = Color(0xFF291F33),
    ink = Color(0xFFF4EEFB),
    inkSoft = Color(0xFFB4A4C6),
    inkFaint = Color(0xFF7C6D93),
    stoneBorder = Color(0xFF3E304C),
    accent = Color(0xFFFF6B4A),
    accentSoft = Color(0x29FF6B4A),
    onAccent = Color(0xFF1E1626),
    accent2 = Color(0xFFFFC94A),
    accent2Soft = Color(0x29FFC94A),
    moss = Color(0xFF8CE0A0),
    amber = Color(0xFFFFD37A),
    rust = Color(0xFFFF8A76),
    isDark = true
)

val LocalSgColors = staticCompositionLocalOf { WarmLightColors }
