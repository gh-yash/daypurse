package bizappz.daypurse.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val base = Typography()

/**
 * System font (no bundled font = smaller APK) with a tighter, bolder display scale for money.
 * Tabular figures keep amounts from jiggling as digits change.
 */
private fun TextStyle.money() = copy(fontFeatureSettings = "tnum", letterSpacing = (-0.5).sp)

val Typography = Typography(
    displayLarge = base.displayLarge.money().copy(fontWeight = FontWeight.SemiBold),
    displayMedium = base.displayMedium.money().copy(fontWeight = FontWeight.SemiBold),
    displaySmall = base.displaySmall.money().copy(fontWeight = FontWeight.Bold),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
    headlineMedium = base.headlineMedium.money().copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)
