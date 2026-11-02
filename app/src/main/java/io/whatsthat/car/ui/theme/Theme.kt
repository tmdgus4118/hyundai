package io.whatsthat.car.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 현대자동차 브랜드 톤을 따른 차량용 다크 테마.
 * Hyundai Blue(#002C5F)를 바탕 깊이감에, Active Blue 계열(#00AAD2)을 상호작용 강조에,
 * Sand(#E4DCD3)를 따뜻한 보조색으로 쓴다. 주행 중 가독성을 위해 고대비·큰 글자를 기본으로 한다.
 */
object WT {
    val Night = Color(0xFF070B11)
    val Surface = Color(0xFF0F1620)
    val SurfaceHigh = Color(0xFF162030)
    val Line = Color(0xFF243142)
    val HyundaiBlue = Color(0xFF002C5F)
    val ActiveBlue = Color(0xFF00AAD2)
    val ActiveBlueDim = Color(0xFF00708C)
    val Sand = Color(0xFFE4DCD3)
    val TextHigh = Color(0xFFF4F6F8)
    val TextMid = Color(0xFFA7B2BF)
    val TextLow = Color(0xFF6B7888)
    val Ok = Color(0xFF3FCB8E)
    val Warn = Color(0xFFF2B33D)
    val Bad = Color(0xFFEF5B5B)
}

private val scheme = darkColorScheme(
    primary = WT.ActiveBlue,
    onPrimary = WT.Night,
    secondary = WT.Sand,
    background = WT.Night,
    surface = WT.Surface,
    surfaceVariant = WT.SurfaceHigh,
    onBackground = WT.TextHigh,
    onSurface = WT.TextHigh,
    onSurfaceVariant = WT.TextMid,
    outline = WT.Line,
)

private val type = Typography(
    displayLarge = TextStyle(fontSize = 56.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp, lineHeight = 64.sp),
    displayMedium = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, lineHeight = 52.sp),
    headlineMedium = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.SemiBold, lineHeight = 38.sp),
    titleLarge = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold, lineHeight = 34.sp),
    titleMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium, lineHeight = 30.sp),
    bodyLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Normal, lineHeight = 36.sp),
    bodyMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Normal, lineHeight = 30.sp),
    labelLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    labelMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
)

@Composable
fun WhatsThatTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, typography = type, content = content)
