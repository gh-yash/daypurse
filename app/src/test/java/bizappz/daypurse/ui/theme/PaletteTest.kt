package bizappz.daypurse.ui.theme

import androidx.compose.ui.graphics.luminance
import bizappz.daypurse.data.model.Accent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteTest {
    @Test
    fun tonesHaveRequestedLightnessForEveryAccentHue() {
        for (hue in listOf(12.0, 72.0, 152.0, 200.0, 262.0, 305.0)) {
            val p = TonalPalette(hue, 52.0)
            for (tone in listOf(10, 20, 30, 40, 80, 90, 95)) {
                assertEquals("hue $hue tone $tone", tone.toDouble(), p.tone(tone).lstar(), 1.0)
            }
        }
    }

    private fun contrast(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color): Double {
        val (hi, lo) = listOf(a.luminance() + 0.05, b.luminance() + 0.05).sortedDescending()
        return (hi / lo).toDouble()
    }

    @Test
    fun textOnKeySurfacesMeetsWcagAaInBothModes() {
        for (accent in Accent.entries) for (dark in listOf(false, true)) {
            val c = colorSchemeFor(accent, dark, pureBlack = false)
            val label = "$accent dark=$dark"
            assertTrue(label, contrast(c.onPrimary, c.primary) >= 4.5)
            assertTrue(label, contrast(c.onSurface, c.surface) >= 4.5)
            assertTrue(label, contrast(c.onPrimaryContainer, c.primaryContainer) >= 4.5)
            assertTrue(label, contrast(c.onSurfaceVariant, c.surface) >= 4.5)
            val hero = appColorsFor(accent, dark, pureBlack = false)
            assertTrue(label, contrast(hero.onHero, hero.heroStart) >= 4.5)
        }
    }
}
