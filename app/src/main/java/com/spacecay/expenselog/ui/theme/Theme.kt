package com.spacecay.expenselog.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class MoneyColors(
    val expense: Color,
    val income: Color,
    val transfer: Color,
    val card: Color,
    val subtle: Color,
    val muted: Color,
    val heroStart: Color,
    val heroEnd: Color,
)

private val LightMoney = MoneyColors(
    expense = Color(0xFFE5484D),
    income = Color(0xFF2E9E6A),
    transfer = Color(0xFF6B7AA1),
    card = Color(0xFFFFFFFF),
    subtle = Color(0xFFF0F2F7),
    muted = Color(0xFF6B7080),
    heroStart = Color(0xFF3B66F5),
    heroEnd = Color(0xFF2443B8),
)

private val DarkMoney = MoneyColors(
    expense = Color(0xFFFF6B70),
    income = Color(0xFF4CCB8F),
    transfer = Color(0xFF9AA8CC),
    card = Color(0xFF181B22),
    subtle = Color(0xFF22262F),
    muted = Color(0xFF9AA0AE),
    heroStart = Color(0xFF2D4FC9),
    heroEnd = Color(0xFF1A2E80),
)

private val LightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF2F5BEA),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE4FF),
    onPrimaryContainer = Color(0xFF0E2878),
    secondary = Color(0xFF5B6478),
    secondaryContainer = Color(0xFFE3E7F1),
    onSecondaryContainer = Color(0xFF1B2232),
    background = Color(0xFFF4F5F9),
    onBackground = Color(0xFF1B1E28),
    surface = Color(0xFFF4F5F9),
    onSurface = Color(0xFF1B1E28),
    surfaceVariant = Color(0xFFE8EAF1),
    onSurfaceVariant = Color(0xFF5E6474),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFEDEFF5),
    outline = Color(0xFFC5C9D4),
    outlineVariant = Color(0xFFE2E5EC),
    error = Color(0xFFE5484D),
)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF8FA8FF),
    onPrimary = Color(0xFF0B1E66),
    primaryContainer = Color(0xFF243A99),
    onPrimaryContainer = Color(0xFFDDE4FF),
    secondary = Color(0xFFB9C1D4),
    secondaryContainer = Color(0xFF2A303D),
    onSecondaryContainer = Color(0xFFDCE1EE),
    background = Color(0xFF0E1015),
    onBackground = Color(0xFFF1F2F6),
    surface = Color(0xFF0E1015),
    onSurface = Color(0xFFF1F2F6),
    surfaceVariant = Color(0xFF22262F),
    onSurfaceVariant = Color(0xFFA9AFBD),
    surfaceContainer = Color(0xFF181B22),
    surfaceContainerLow = Color(0xFF15181E),
    surfaceContainerHigh = Color(0xFF1E2129),
    surfaceContainerHighest = Color(0xFF262A33),
    outline = Color(0xFF4A505E),
    outlineVariant = Color(0xFF2C313B),
    error = Color(0xFFFF6B70),
)

val LocalMoney = staticCompositionLocalOf { LightMoney }

private val AppTypography = Typography().run {
    copy(
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

val AmountStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun AppTheme(mode: Int, content: @Composable () -> Unit) {
    val dark = when (mode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    CompositionLocalProvider(LocalMoney provides if (dark) DarkMoney else LightMoney) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

object Money3 {
    val colors: MoneyColors @Composable get() = LocalMoney.current
}
