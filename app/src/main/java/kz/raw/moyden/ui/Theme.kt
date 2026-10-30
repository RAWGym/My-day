package kz.raw.moyden.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kz.raw.moyden.R
import kz.raw.moyden.data.Settings

data class Palette(
    val id: String,
    val name: String,
    val dark: Boolean,
    val bg: Color,
    val card: Color,
    val cardAlt: Color,
    val accent: Color,
    val onAccent: Color,
    val text: Color,
    val muted: Color,
    val good: Color,
    val bad: Color,
    val med: Color,
    val event: Color,
)

val PALETTES = listOf(
    Palette("raw", "Тёплый RAW", false, Color(0xFFF4F1EB), Color(0xFFFFFDF9), Color(0xFFEAE3D7),
        Color(0xFFB8935A), Color(0xFFFFFFFF), Color(0xFF1C1814), Color(0xFF7D746C),
        Color(0xFF5E8B5A), Color(0xFFB4533F), Color(0xFF7A63A8), Color(0xFF2F6F8F)),
    Palette("graphite", "Графит", true, Color(0xFF151412), Color(0xFF211F1C), Color(0xFF2D2A26),
        Color(0xFFC9A46A), Color(0xFF1C1814), Color(0xFFF4F1EB), Color(0xFFA39A90),
        Color(0xFF8DBB86), Color(0xFFE07A63), Color(0xFFB39DDB), Color(0xFF7FB3D0)),
    Palette("ivory", "Слоновая кость", false, Color(0xFFF6F2EB), Color(0xFFFFFFFF), Color(0xFFECE6DC),
        Color(0xFF8A8178), Color(0xFFFFFFFF), Color(0xFF1E1E1E), Color(0xFF8A8178),
        Color(0xFF5E8B5A), Color(0xFFB4533F), Color(0xFFC2A46D), Color(0xFF4F6D7A)),
    Palette("sage", "Шалфей", false, Color(0xFFEEF2EC), Color(0xFFFFFFFF), Color(0xFFDDE6DA),
        Color(0xFF5E7D63), Color(0xFFFFFFFF), Color(0xFF1F2A22), Color(0xFF6B786D),
        Color(0xFF3F7A48), Color(0xFFB4533F), Color(0xFF8A6FB0), Color(0xFF2F6F8F)),
    Palette("sea", "Море", false, Color(0xFFEAF1F5), Color(0xFFFFFFFF), Color(0xFFD7E5EE),
        Color(0xFF2F6F8F), Color(0xFFFFFFFF), Color(0xFF13222B), Color(0xFF5F7280),
        Color(0xFF3F7A48), Color(0xFFC0503A), Color(0xFF7A63A8), Color(0xFFC08A3E)),
    Palette("lavender", "Лаванда", false, Color(0xFFF1EEF6), Color(0xFFFFFFFF), Color(0xFFE3DDEE),
        Color(0xFF7A63A8), Color(0xFFFFFFFF), Color(0xFF221C2E), Color(0xFF746C82),
        Color(0xFF4F8A57), Color(0xFFB4533F), Color(0xFFB5676E), Color(0xFF2F6F8F)),
    Palette("rose", "Пудра", false, Color(0xFFF7EEEE), Color(0xFFFFFFFF), Color(0xFFEFDFDF),
        Color(0xFFB5676E), Color(0xFFFFFFFF), Color(0xFF2B1B1D), Color(0xFF85706F),
        Color(0xFF4F8A57), Color(0xFFB4433F), Color(0xFF7A63A8), Color(0xFF2F6F8F)),
    Palette("night", "Ночь", true, Color(0xFF0F1520), Color(0xFF18202E), Color(0xFF232D3E),
        Color(0xFF7FA7E0), Color(0xFF0F1520), Color(0xFFE8EEF7), Color(0xFF93A1B5),
        Color(0xFF86C08E), Color(0xFFE88A78), Color(0xFFB9A3E3), Color(0xFFE2B66E)),
    Palette("contrast", "Контраст", false, Color(0xFFFFFFFF), Color(0xFFF3F3F3), Color(0xFFE2E2E2),
        Color(0xFF1A4FD0), Color(0xFFFFFFFF), Color(0xFF000000), Color(0xFF3D3D3D),
        Color(0xFF1C7A2E), Color(0xFFC21E1E), Color(0xFF6A2FB0), Color(0xFF00647A)),
)

data class FontChoice(val id: String, val name: String, val family: FontFamily)

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun variable(res: Int): FontFamily = FontFamily(
    listOf(400, 500, 600, 700).map { w ->
        Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    }
)

val FONTS = listOf(
    FontChoice("nunito", "Мягкий", variable(R.font.nunito)),
    FontChoice("manrope", "Современный", variable(R.font.manrope)),
    FontChoice("system", "Как в телефоне", FontFamily.Default),
    FontChoice("lora", "С засечками", variable(R.font.lora)),
    FontChoice("comfortaa", "Округлый", variable(R.font.comfortaa)),
)

val TEXT_SIZES = listOf(1.0f to "Обычный", 1.15f to "Крупный", 1.3f to "Очень крупный", 1.5f to "Огромный")

val LocalPalette = staticCompositionLocalOf { PALETTES.first() }

fun paletteOf(id: String) = PALETTES.firstOrNull { it.id == id } ?: PALETTES.first()
fun fontOf(id: String) = FONTS.firstOrNull { it.id == id } ?: FONTS.first()

private fun scheme(p: Palette): ColorScheme {
    val base = if (p.dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = p.accent, onPrimary = p.onAccent,
        primaryContainer = p.cardAlt, onPrimaryContainer = p.text,
        secondary = p.accent, onSecondary = p.onAccent,
        secondaryContainer = p.cardAlt, onSecondaryContainer = p.text,
        tertiary = p.med,
        background = p.bg, onBackground = p.text,
        surface = p.card, onSurface = p.text,
        surfaceVariant = p.cardAlt, onSurfaceVariant = p.muted,
        surfaceContainer = p.card, surfaceContainerHigh = p.card, surfaceContainerHighest = p.cardAlt,
        surfaceContainerLow = p.card, surfaceContainerLowest = p.bg,
        outline = p.muted.copy(alpha = 0.5f), outlineVariant = p.muted.copy(alpha = 0.25f),
        error = p.bad,
    )
}

private fun typography(f: FontFamily): Typography {
    fun s(size: Int, w: Int, lh: Int) = TextStyle(fontFamily = f, fontWeight = FontWeight(w),
        fontSize = size.sp, lineHeight = lh.sp)
    return Typography(
        displayLarge = s(52, 600, 58), displayMedium = s(44, 600, 50), displaySmall = s(36, 600, 42),
        headlineLarge = s(30, 700, 36), headlineMedium = s(26, 700, 32), headlineSmall = s(22, 700, 28),
        titleLarge = s(20, 700, 26), titleMedium = s(17, 600, 23), titleSmall = s(15, 600, 20),
        bodyLarge = s(16, 400, 23), bodyMedium = s(15, 400, 21), bodySmall = s(13, 400, 18),
        labelLarge = s(15, 600, 20), labelMedium = s(13, 600, 17), labelSmall = s(12, 500, 16),
    )
}

@Composable
fun MoyDenTheme(content: @Composable () -> Unit) {
    val prefs by Settings.prefs.collectAsStateWithLifecycle()
    val pal = paletteOf(prefs.themeId)
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalPalette provides pal,
        LocalDensity provides Density(density.density, density.fontScale * prefs.textScale),
    ) {
        MaterialTheme(
            colorScheme = scheme(pal),
            typography = typography(fontOf(prefs.fontId).family),
            shapes = Shapes(
                extraSmall = RoundedCornerShape(10.dp), small = RoundedCornerShape(14.dp),
                medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp),
                extraLarge = RoundedCornerShape(30.dp),
            ),
            content = content,
        )
    }
}
