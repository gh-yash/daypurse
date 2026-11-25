package bizappz.daypurse.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import bizappz.daypurse.data.model.Accent
import bizappz.daypurse.data.model.ThemeMode

/** Softer, larger corners throughout (cards 24dp, sheets/dialogs 28–32dp). */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun DayPurseTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accent: Accent = Accent.EMERALD,
    pureBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = remember(accent, dark, pureBlack) { colorSchemeFor(accent, dark, pureBlack) }
    val appColors = remember(accent, dark, pureBlack) { appColorsFor(accent, dark, pureBlack) }
    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(colorScheme = colors, typography = Typography, shapes = AppShapes, content = content)
    }
}

val LocalAppColors = staticCompositionLocalOf { appColorsFor(Accent.EMERALD, dark = false, pureBlack = false) }

/** Shortcut: `AppTheme.colors.card` etc. */
object AppTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppColors.current
}
