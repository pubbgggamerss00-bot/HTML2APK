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

// ---------------------------------------------------------------------------
// palette: deep-navy surfaces + emerald accents — the ORIGINAL app look.
// Every value below is byte-identical to the theme the app shipped with
// before the 3.5/3.6 recolor; do not "modernize" these values.
// ---------------------------------------------------------------------------
val SurfaceDim = Color(0xFF080E1C)
val SurfaceBase = Color(0xFF0B1326)
val ContainerLowest = Color(0xFF050A15)
val ContainerLow = Color(0xFF0F182E)
val Container = Color(0xFF14203B)
val ContainerHigh = Color(0xFF1B294C)
val ContainerHighest = Color(0xFF24345D)
val SurfaceVariantNavy = Color(0xFF1E2A4A)
val Outline = Color(0xFF4B6088)
val OutlineVariant = Color(0xFF223150)
val Primary = Color(0xFF4EDEA3)
val PrimaryContainerNavy = Color(0xFF005236)
val OnPrimary = Color(0xFF003824)
val PrimaryGlow = Color(0x474EDEA3)
val Emerald = Color(0xFF10B981)
val Emerald400 = Color(0xFF34D399)
val EmeraldDeep = Color(0xFF065F46)
val EmeraldChipBg = Color(0xFF064E3B)
val EmeraldChipText = Color(0xFF6EE7B7)
val Secondary = Color(0xFF7BD0FF)
val OnSecondaryNavy = Color(0xFF003828)
val SecondaryContainerNavy = Color(0xFF004C69)
val OnSecondaryContainerNavy = Color(0xFF87D2B2)
val TertiaryLav = Color(0xFFA8B5FF)
val OnSurfaceNavy = Color(0xFFE2E8F8)
val OnSurfaceVariantNavy = Color(0xFF8EA2CA)
val TextWhite = Color(0xFFFFFFFF)
val ErrorRed = Color(0xFFEF4444)
val OnErrorNavy = Color(0xFF690005)
val ErrorContainerNavy = Color(0xFF450A0A)
val ErrorOnContainerNavy = Color(0xFFFCA5A5)
val Warning = Color(0xFFF59E0B)
val LogStdout = Color(0xFF94A3B8)
val LogError = Color(0xFFEF4444)
val LogSuccess = Color(0xFF10B981)
val LogStage = Color(0xFF38BDF8)

// ---- live theme (تم زنده) aurora colors ----
val AuroraEmerald = Color(0xFF10B981)
val AuroraSky = Color(0xFF38BDF8)
val AuroraLavender = Color(0xFFA8B5FF)

/** The exact Material3 slot mapping the original app used. */
val EmeraldDarkScheme: ColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainerNavy,
    onPrimaryContainer = EmeraldChipText,
    secondary = Secondary,
    onSecondary = OnSecondaryNavy,
    secondaryContainer = SecondaryContainerNavy,
    onSecondaryContainer = OnSecondaryContainerNavy,
    tertiary = TertiaryLav,
    background = SurfaceDim,
    onBackground = OnSurfaceNavy,
    surface = SurfaceDim,
    onSurface = OnSurfaceNavy,
    surfaceVariant = SurfaceVariantNavy,
    onSurfaceVariant = OnSurfaceVariantNavy,
    error = ErrorRed,
    onError = OnErrorNavy,
    errorContainer = ErrorContainerNavy,
    onErrorContainer = ErrorOnContainerNavy,
    outline = Outline,
    outlineVariant = OutlineVariant,
)

// ---------------------------------------------------------------------------
// Typography — Vazirmatn everywhere, JetBrains Mono for terminal labels.
// ---------------------------------------------------------------------------
val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrainsmono_regular, FontWeight.Normal),
    Font(R.font.jetbrainsmono_medium, FontWeight.Medium),
    Font(R.font.jetbrainsmono_bold, FontWeight.Bold),
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

/** Dark navy/emerald theme, always RTL (the whole app is Persian-first). */
@Composable
fun HTML2APKTheme(content: @Composable () -> Unit) {
    // The design is dark-only; isSystemInDarkTheme kept for future light theme work.
    isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = EmeraldDarkScheme,
        typography = AppTypography,
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            content()
        }
    }
}
