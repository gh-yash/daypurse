package bizappz.daypurse.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import bizappz.daypurse.data.model.Accent
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Builds Material 3 colour schemes from an accent hue, the way Material's tonal palettes work:
 * a "tone" is perceptual lightness (CIE L*, 0 = black, 100 = white), so tone 40 of any hue
 * has the same contrast against tone 90. Done in CIE LCh with chroma reduced to stay in sRGB.
 */
internal class TonalPalette(private val hue: Double, private val chroma: Double) {
    private val cache = HashMap<Int, Color>()
    fun tone(t: Int): Color = cache.getOrPut(t) { lchToColor(t.toDouble(), chroma, hue) }
}

/** LCh hue (degrees) and chroma for each accent. */
private val Accent.hue: Double
    get() = when (this) {
        Accent.EMERALD -> 152.0
        Accent.TEAL -> 200.0
        Accent.OCEAN -> 262.0
        Accent.VIOLET -> 305.0
        Accent.ROSE -> 12.0
        Accent.AMBER -> 72.0
    }

/** Swatch colour for the accent picker. */
fun Accent.swatch(): Color = lchToColor(50.0, 55.0, hue)

private val Error = TonalPalette(25.0, 80.0)

/** Colours outside the Material roles: the gradient "hero" card and the card surface. */
data class AppColors(
    val heroStart: Color,
    val heroEnd: Color,
    val onHero: Color,
    val heroOverStart: Color,
    val heroOverEnd: Color,
    val card: Color,
    val isDark: Boolean,
)

fun appColorsFor(accent: Accent, dark: Boolean, pureBlack: Boolean): AppColors {
    val p = TonalPalette(accent.hue, 52.0)
    val n = TonalPalette(accent.hue, 3.0)
    return if (!dark) {
        AppColors(p.tone(42), p.tone(28), Color.White, Error.tone(45), Error.tone(32), n.tone(100), false)
    } else {
        AppColors(p.tone(34), p.tone(20), p.tone(98), Error.tone(36), Error.tone(22), if (pureBlack) n.tone(8) else n.tone(12), true)
    }
}

fun colorSchemeFor(accent: Accent, dark: Boolean, pureBlack: Boolean): ColorScheme {
    val h = accent.hue
    val p = TonalPalette(h, 52.0)
    val s = TonalPalette(h, 14.0)
    val t = TonalPalette((h + 60.0) % 360.0, 28.0)
    val n = TonalPalette(h, 3.0) // near-neutral surfaces with a hint of the accent
    val nv = TonalPalette(h, 7.0)

    return if (!dark) {
        lightColorScheme(
            primary = p.tone(40), onPrimary = p.tone(100), primaryContainer = p.tone(90), onPrimaryContainer = p.tone(10),
            inversePrimary = p.tone(80),
            secondary = s.tone(40), onSecondary = s.tone(100), secondaryContainer = s.tone(90), onSecondaryContainer = s.tone(10),
            tertiary = t.tone(40), onTertiary = t.tone(100), tertiaryContainer = t.tone(90), onTertiaryContainer = t.tone(10),
            error = Error.tone(40), onError = Error.tone(100), errorContainer = Error.tone(90), onErrorContainer = Error.tone(10),
            background = n.tone(96), onBackground = n.tone(10), surface = n.tone(96), onSurface = n.tone(10),
            surfaceVariant = nv.tone(90), onSurfaceVariant = nv.tone(30), outline = nv.tone(50), outlineVariant = nv.tone(80),
            inverseSurface = n.tone(20), inverseOnSurface = n.tone(95), surfaceTint = p.tone(40), scrim = Color.Black,
            surfaceBright = n.tone(98), surfaceDim = n.tone(87),
            surfaceContainerLowest = n.tone(100), surfaceContainerLow = n.tone(98), surfaceContainer = n.tone(94),
            surfaceContainerHigh = n.tone(91), surfaceContainerHighest = n.tone(88),
        )
    } else {
        val black = pureBlack
        darkColorScheme(
            primary = p.tone(80), onPrimary = p.tone(20), primaryContainer = p.tone(30), onPrimaryContainer = p.tone(90),
            inversePrimary = p.tone(40),
            secondary = s.tone(80), onSecondary = s.tone(20), secondaryContainer = s.tone(30), onSecondaryContainer = s.tone(90),
            tertiary = t.tone(80), onTertiary = t.tone(20), tertiaryContainer = t.tone(30), onTertiaryContainer = t.tone(90),
            error = Error.tone(80), onError = Error.tone(20), errorContainer = Error.tone(30), onErrorContainer = Error.tone(90),
            background = if (black) Color.Black else n.tone(6), onBackground = n.tone(90),
            surface = if (black) Color.Black else n.tone(6), onSurface = n.tone(90),
            surfaceVariant = nv.tone(30), onSurfaceVariant = nv.tone(80), outline = nv.tone(60), outlineVariant = nv.tone(30),
            inverseSurface = n.tone(90), inverseOnSurface = n.tone(20), surfaceTint = p.tone(80), scrim = Color.Black,
            surfaceBright = n.tone(24), surfaceDim = if (black) Color.Black else n.tone(6),
            surfaceContainerLowest = if (black) Color.Black else n.tone(4),
            surfaceContainerLow = if (black) n.tone(6) else n.tone(10),
            surfaceContainer = if (black) n.tone(8) else n.tone(12),
            surfaceContainerHigh = if (black) n.tone(12) else n.tone(17),
            surfaceContainerHighest = if (black) n.tone(17) else n.tone(22),
        )
    }
}

// --- CIE LCh(ab) → sRGB, D65 -------------------------------------------------------------

internal fun lchToColor(l: Double, chroma: Double, hueDeg: Double): Color {
    if (l <= 0.0) return Color.Black
    if (l >= 100.0) return Color.White
    // Largest chroma ≤ requested that is still displayable at this lightness and hue.
    var lo = 0.0
    var hi = chroma
    var best = labToRgb(l, 0.0, hueDeg)!!
    labToRgb(l, chroma, hueDeg)?.let { return it.toColor() }
    repeat(18) {
        val mid = (lo + hi) / 2
        val rgb = labToRgb(l, mid, hueDeg)
        if (rgb != null) { best = rgb; lo = mid } else hi = mid
    }
    return best.toColor()
}

private fun DoubleArray.toColor() = Color(this[0].toFloat(), this[1].toFloat(), this[2].toFloat())

/** Returns gamma-encoded sRGB in 0..1, or null if out of gamut. */
private fun labToRgb(l: Double, c: Double, hueDeg: Double): DoubleArray? {
    val rad = Math.toRadians(hueDeg)
    val a = c * cos(rad)
    val b = c * sin(rad)
    val fy = (l + 16.0) / 116.0
    val fx = fy + a / 500.0
    val fz = fy - b / 200.0
    val x = 0.95047 * finv(fx)
    val y = if (l > 8.0) fy * fy * fy else l / 903.2963
    val z = 1.08883 * finv(fz)
    val lin = doubleArrayOf(
        3.2404542 * x - 1.5371385 * y - 0.4985314 * z,
        -0.9692660 * x + 1.8760108 * y + 0.0415560 * z,
        0.0556434 * x - 0.2040259 * y + 1.0572252 * z,
    )
    val eps = 1e-4
    if (lin.any { it < -eps || it > 1 + eps }) return null
    return DoubleArray(3) { i ->
        val v = lin[i].coerceIn(0.0, 1.0)
        if (v <= 0.0031308) 12.92 * v else 1.055 * v.pow(1 / 2.4) - 0.055
    }
}

private fun finv(t: Double): Double {
    val t3 = t * t * t
    return if (t3 > 0.008856) t3 else (t - 16.0 / 116.0) / 7.787
}

/** Perceptual lightness (L*) of a colour, for tests. */
internal fun Color.lstar(): Double {
    fun lin(c: Float) = if (c <= 0.04045f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    val y = 0.2126729 * lin(red) + 0.7151522 * lin(green) + 0.0721750 * lin(blue)
    return if (y > 0.008856) 116 * cbrt(y) - 16 else 903.2963 * y
}
