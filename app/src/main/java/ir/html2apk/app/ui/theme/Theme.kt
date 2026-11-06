package ir.html2apk.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import ir.html2apk.app.R

// ---- palette: dark emerald ------------------------------------------------
val DarkBackground = Color(0xFF0A1411)
val DarkSurface = Color(0xFF0F1F19)
val DarkSurfaceVariant = Color(0xFF163026)
val DarkOutline = Color(0xFF2A4A3C)
val EmeraldPrimary = Color(0xFF34D399)
val EmeraldOnPrimary = Color(0xFF052E1C)
val EmeraldSecondary = Color(0xFF0EA371)
val AmberTertiary = Color(0xFFFBBF24)
val TextPrimary = Color(0xFFE7F4EC)
val TextSecondary = Color(0xFF93B2A3)
val ErrorRed = Color(0xFFF87171)
val TerminalBackground = Color(0xFF050C09)
val TerminalGreen = Color(0xFF6EE7A8)

val AppColorScheme: ColorScheme = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = EmeraldOnPrimary,
    secondary = EmeraldSecondary,
    onSecondary = TextPrimary,
    tertiary = AmberTertiary,
    onTertiary = EmeraldOnPrimary,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed,
    onError = Color(0xFF1A0000),
    outline = DarkOutline,
)

// ---- fonts ---------------------------------------------------------------
val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrainsmono_regular, FontWeight.Normal),
)

private val BaseTypography = Typography()

val AppTypography: Typography = Typography(
    displayLarge = BaseTypography.displayLarge.copy(fontFamily = Vazirmatn),
    displayMedium = BaseTypography.displayMedium.copy(fontFamily = Vazirmatn),
    displaySmall = BaseTypography.displaySmall.copy(fontFamily = Vazirmatn),
    headlineLarge = BaseTypography.headlineLarge.copy(fontFamily = Vazirmatn),
    headlineMedium = BaseTypography.headlineMedium.copy(fontFamily = Vazirmatn),
    headlineSmall = BaseTypography.headlineSmall.copy(fontFamily = Vazirmatn),
    titleLarge = BaseTypography.titleLarge.copy(fontFamily = Vazirmatn),
    titleMedium = BaseTypography.titleMedium.copy(fontFamily = Vazirmatn),
    titleSmall = BaseTypography.titleSmall.copy(fontFamily = Vazirmatn),
    bodyLarge = BaseTypography.bodyLarge.copy(fontFamily = Vazirmatn),
    bodyMedium = BaseTypography.bodyMedium.copy(fontFamily = Vazirmatn),
    bodySmall = BaseTypography.bodySmall.copy(fontFamily = Vazirmatn),
    labelLarge = BaseTypography.labelLarge.copy(fontFamily = Vazirmatn),
    labelMedium = BaseTypography.labelMedium.copy(fontFamily = Vazirmatn),
    labelSmall = BaseTypography.labelSmall.copy(fontFamily = JetBrainsMono),
)

/** Dark emerald theme, always RTL (the whole app is Persian-first). */
@Composable
fun HTML2APKTheme(content: @Composable () -> Unit) {
    // The design is dark-only; isSystemInDarkTheme kept for future light theme work.
    isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = AppTypography,
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            content()
        }
    }
}
